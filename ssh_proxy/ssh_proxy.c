#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <stdarg.h>
#include <errno.h>
#include <netdb.h>
#include <sys/types.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <unistd.h>

#define EXIT_CH 3

static void die(const char *msg) {
	fprintf(stderr, "fatal: %s\n", msg);
	exit(1);
}

static ssize_t xread(int fd, char *buf, size_t n) {
	for (;;) {
		ssize_t r = read(fd, buf, n);
		if (r < 0) {
			if (errno == EINTR)
				continue;
			die("cannot read from fd");
		}
		return r;
	}
}

static ssize_t xwrite(int fd, char *buf, size_t n) {
	for (;;) {
		ssize_t w = write(fd, buf, n);
		if (w < 0) {
			if (errno == EINTR)
				continue;
			die("cannot write to fd");
		}
		return w;
	}
}

static void write_or_die(int fd, char *buf, size_t n) {
	while (n > 0) {
		ssize_t w = xwrite(fd, buf, n);
		if (w <= 0)
			die("cannot write to fd");
		buf += w;
		n -= w;
	}
}

static fd_set read_fds;
static fd_set write_fds;
static fd_set error_fds;

struct copy {
	int in_fd;
	int out_fd;

	unsigned demux:1;
	ssize_t pkt_len;

	ssize_t pos;
	ssize_t cnt;
	char buf[65536];
};

static void read_pkt_line_header(struct copy *c) {
	ssize_t n = 0;
	int need = 5;
	char tmp[6];

	while (n < need) {
		ssize_t r = xread(c->in_fd, tmp + n, need - n);
		if (!r)
			exit(0);
		if (r < 0)
			die("cannot read pkt-line header");
		n += r;
		if (n >= 5 && tmp[4] == EXIT_CH)
			need = 6;
	}

	c->out_fd = tmp[4];
	tmp[4] = 0;

	if (c->out_fd == EXIT_CH)
		exit(((int) tmp[5]) & 0xff);
	c->pkt_len = strtol(tmp, NULL, 16) - 5;
}

static void begin_read(struct copy *c) {
	FD_SET(c->in_fd, &read_fds);
	FD_SET(c->in_fd, &error_fds);
	FD_CLR(c->out_fd, &write_fds);
}

static void begin_write(struct copy *c) {
	FD_CLR(c->in_fd, &read_fds);
	FD_CLR(c->in_fd, &error_fds);
	FD_SET(c->out_fd, &write_fds);
}

static void do_copy(struct copy *c) {
	if (!c->cnt) {
		if (!FD_ISSET(c->in_fd, &read_fds)
		 && !FD_ISSET(c->in_fd, &error_fds))
			return;

		if (c->demux && !c->pkt_len)
			read_pkt_line_header(c);
		ssize_t sz = c->demux ? c->pkt_len : sizeof(c->buf);
		c->pos = 0;
		c->cnt = xread(c->in_fd, c->buf, sz);
		if (!c->cnt)
			exit(0);
		if (c->demux)
			c->pkt_len -= c->cnt;
		begin_write(c);
		return;
	}

	if (!FD_ISSET(c->out_fd, &write_fds)) {
		FD_SET(c->out_fd, &write_fds);
		return;
	}

	ssize_t n = xwrite(c->out_fd, c->buf + c->pos, c->cnt);
	c->pos += n;
	c->cnt -= n;

	if (!c->cnt)
		begin_read(c);
	else
		begin_write(c);
}

static void pkt_write(int sock, char *fmt, ...) {
		char len[5];
		char buf[1000];
		int n;
		va_list ap;

		va_start(ap, fmt);
		n = vsnprintf(buf, sizeof(buf), fmt, ap);
		va_end(ap);

		sprintf(len, "%04x", n + 4);
		write_or_die(sock, len, 4);
		write_or_die(sock, buf, n);
}

static int dial_gerrit(char *site_path) {
	static const char *info_name = "tmp/sshd_backend";
	char *info_path;
	char *host = "127.0.0.1";
	int port;
	int fd;
	int sock;
	struct hostent *backend;
	struct sockaddr_in addr;
	char info_block[512];
	int info_len;
	char *p;
	char *key;
	
	info_path = malloc(strlen(site_path) + strlen(info_name) + 2);
	sprintf(info_path, "%s/%s", site_path, info_name);
	fd = open(info_path, O_RDONLY);
	if (fd < 0)
		die("cannot find sshd_backend");
	info_len = read(fd, info_block, sizeof(info_block) - 1);
	if (info_len <= 0)
		die("cannot find sshd_backend");
	close(fd);
	if (info_len > 0 && info_block[info_len - 1] == '\n')
		info_block[info_len - 1] = 0;
	else
		info_block[info_len] = 0;
	free(info_path);

	port = strtoul(info_block, &p, 10);
	if (*p != '\n')
		die("invalid sshd_backend");
	key = p + 1;

	sock = socket(AF_INET, SOCK_STREAM, 0);
	if (sock < 0)
		die("cannot create socket");

	backend = gethostbyname(host);
	if (!backend)
		die("cannot resolve backend");

	memset(&addr, 0, sizeof(addr));
	addr.sin_family = AF_INET;
	addr.sin_port = htons(port);
	memcpy(&addr.sin_addr.s_addr, backend->h_addr, backend->h_length);

	if (connect(sock, (struct sockaddr*)&addr, sizeof(addr)) < 0)
		die("cannot connect to backend");

	pkt_write(sock, "auth %s\n", key);
	return sock;
}

static struct copy cin;
static struct copy cout;

int main(int argc, char **argv) {
	int sock;
	int max_fd;

	if (argc < 3)
		die("usage: ssh_proxy site_path {exec|keys user}");
	sock = dial_gerrit(argv[1]);

	if (!strcmp("keys", argv[2])) {
		if (argc != 4)
			die("usage: ssh_proxy keys user");
		pkt_write(sock, "keys %s\n", argv[3]);
	} else if (!strcmp("exec", argv[2])) {
		char *conn = getenv("SSH_CONNECTION");
		char *user = getenv("LOGNAME");
		char *cmd = getenv("SSH_ORIGINAL_COMMAND");
		if (argc != 3)
			die("usage: ssh_proxy exec");
		if (!conn || !user || !cmd)
			die("usage: must be run by sshd");
		pkt_write(sock, "conn %s\n", conn);
		pkt_write(sock, "user %s\n", user);
		pkt_write(sock, "exec %s", cmd);
	} else
		die("usage: ssh_proxy site_path {exec|keys user}");

	cin.in_fd = 0;
	cin.out_fd = sock;
	begin_read(&cin);

	cout.in_fd = sock;
	cout.out_fd = 1;
	cout.demux = 1;
	begin_read(&cout);

	max_fd = sock + 1;
	for (;;) {
		int n = select(max_fd, &read_fds, &write_fds, &error_fds, NULL);
		if (n < 0 && (errno == EAGAIN || errno == EINTR))
			continue;
		if (n < 0)
			die("select error");

		do_copy(&cin);
		do_copy(&cout);
	}
	close(sock);
	return 0;
}
