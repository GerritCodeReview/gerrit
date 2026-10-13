// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.gpg.testing;

/**
 * Test keys specific to web-of-trust checks.
 *
 * <p>In the following diagrams, the notation <code>M---N</code> indicates N trusts M, and an 'x'
 * indicates the key is expired.
 *
 * <p>
 *
 * <pre>
 *  A---Bx
 *   \
 *    \---C---D
 *         \
 *          \---Ex
 *
 *  D and E trust C to be a valid introducer of depth 2.
 *
 * F---G---F, in a cycle.
 *
 * H---I---J, but J is only trusted to length 1.
 * </pre>
 */
public class TestTrustKeys {
  /**
   * pub 2048R/9FD0D396 2010-08-29 Key fingerprint = E401 17FC 4BF4 17BD 8F93 DEB1 D25A D07A 9FD0
   * D396 uid Testuser A &lt;testa@example.com&gt; sub 2048R/F5C099DB 2010-08-29
   */
  public static TestKey keyA() throws Exception {
    return new TestKey(
        """
        -----BEGIN PGP PUBLIC KEY BLOCK-----
        Version: GnuPG v1
        
        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
        =DAMW
        -----END PGP PUBLIC KEY BLOCK-----
        """,
        """
        -----BEGIN PGP PRIVATE KEY BLOCK-----
        Version: GnuPG v1
        
        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
        =FLdD
        -----END PGP PRIVATE KEY BLOCK-----
        """);
  }

  /**
   * pub 2048R/B007D490 2010-08-29 [expired: 2011-08-29] Key fingerprint = 355D 5B98 FECE 6199 83CD
   * C91D 5760 6987 B007 D490 uid Testuser B &lt;testb@example.com&gt;
   */
  public static TestKey keyB() throws Exception {
    return new TestKey(
        """
        -----BEGIN PGP PUBLIC KEY BLOCK-----
        Version: GnuPG v1
        
        mQENBEx6ntMBCADG7j/nuI+VvvNbPY9nnfLrAc3KTj0Z+DMxxUMYoZNLjTw1szQ0
        PuKKACiiSA9Oyj4R0aIhWdIR9iYxp6gQdje3yewzoqMwE+t5onYDpdX9QDFXyEzF
        UPWCjA7OSji1G6fyWakiYxKseqyRXOdHXI5TqMikBalmSpwwvmik0cfRGO+l6qvM
        mVJlcn6mkZB0d8WOPV8j8rFxmVSPn9SVP9L8HaFWv1uI9EY3zXbfNeDNgNeTWIMY
        75saINBA2LALBQ54u52GoSbaR8ukZYAjjkif3WIFI8B9xREwjUBLFy3E357aGyLZ
        jE8nsmPk4MDxDaeDNoSHJjcxtDWQJBub3u1zABEBAAG0HlRlc3R1c2VyIEIgPHRl
        c3RiQGV4YW1wbGUuY29tPokBPgQTAQIAKAUCTHqe0wIbAwUJAeEzgAYLCQgHAwIG
        FQgCCQoLBBYCAwECHgECF4AACgkQV2Bph7AH1JD0nQf/Vm+/Mvl99/y3Qw10S6et
        H6NYWDUeAKXe9mfXBJ39HdtlF50jZ5NzSwksAOSQtQZJ3tQQeElXB29cZDvAscva
        RiTtt+KUxDZSYbEHrC0EO7w0Wi5ltwaWdXnoitMOgPZ/grL7UpUbL8rB1evfLbhm
        AqC/6kgHuXeY/7EAzwU3o0wKbmfx1sh8AyQSi4unUwIDCV1RIAP0+ZfJSg0WwGoS
        JB5+lKajtIE6kMn9m8CWM66/zxSCY3XLcoXvjVxCYPwwgYSyje8dDxxOI+x7uj2I
        IjM5RHQ9hTsR7NQ9JUTFmpKZlcdah93NZLKJAFLUtOPjMa5d5t2O0ZOxZ5ftlhHp
        Q7kBDQRMep7TAQgAwOuLBXnACIsd879ld/vLcn8umpKV8MIUjrqOMjR0rNKpCUDw
        LxL4uVh3q/ksESHnQPPqxFYkgeA66SYrx4jwZjbZ5vv9BW99LHe8lSahqrJA9A9g
        5iw5hH+2ZWrGlu3P65UdQUJW+JaDx1IIBt3BbmdGDuKF/ESsy9qxEKq7tKqHI2JL
        Ed+6OIwWblU7ZogfiNpgZJ0lapxTe84mGsD0TowGTu5re/8wIJf1f2q4PuG+L9OZ
        0ZD5i9s1MAxdw4OD+705owPCQnqsr18nH9aUBHWJn9NCXb3jL7QGaId84Yq8SRlK
        wHSRtHLLJoowJ5fXw5UbZcUtRUergxFRwae87wARAQABiQElBBgBAgAPBQJMep7T
        AhsMBQkB4TOAAAoJEFdgaYewB9SQMbsH/iu1HY7OMJxd8EkfxairRNec/v9uEvYQ
        XqfEPw/Hihdef1TY8vB69ymAPd89e1PRDj1m+0/RivO045qFP7lbWMkjKeR9dXXe
        UzIEsTUJ1CNnA7C3fo11NBVg59E0d84bMKQx7n4AZkljgKFKghUb6OJZiWRdh+8W
        0I95JI2R7nMYw3L8/sSGxt+Vjhs9acB1DldbyYbJitYA4fhVZQH9zgeuhQqdCULQ
        ZexpkQqvG0o4iJKO4yeJNHdeM+NwH38wXfzydtEv6Dxz/YZSTwt08p97l6DQ//H7
        wek1LcqeX47YFa9Ftns8Y8fjh4S8Kyi1F6BhZKbsdDqg2hA+0AFv7LA=
        =tmW1
        -----END PGP PUBLIC KEY BLOCK-----
        """,
        """
        -----BEGIN PGP PRIVATE KEY BLOCK-----
        Version: GnuPG v1
        
        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
        =uFLT
        -----END PGP PRIVATE KEY BLOCK-----
        """);
  }

  /**
   * pub 2048R/D24FE467 2010-08-29 Key fingerprint = 6C21 10AC F4FC 1C7B F270 C00E 641F 1193 D24F
   * E467 uid Testuser C &lt;testc@example.com&gt; sub 2048R/DBECD4FA 2010-08-29
   */
  public static TestKey keyC() throws Exception {
    return new TestKey(
        """
        -----BEGIN PGP PUBLIC KEY BLOCK-----
        Version: GnuPG v1
        
        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
        =LtMR
        -----END PGP PUBLIC KEY BLOCK-----
        """,
        """
        -----BEGIN PGP PRIVATE KEY BLOCK-----
        Version: GnuPG v1
        
        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
        =5pIh
        -----END PGP PRIVATE KEY BLOCK-----
        """);
  }

  /**
   * pub 2048R/0FDD3677 2010-08-29 Key fingerprint = C96C 5E9D 669C 448A D1B9 BEB5 A991 E2D5 0FDD
   * 3677 uid Testuser D &lt;testd@example.com&gt; sub 2048R/CAB81AE0 2010-08-29
   */
  public static TestKey keyD() throws Exception {
    return new TestKey(
        """
        -----BEGIN PGP PUBLIC KEY BLOCK-----
        Version: GnuPG v1
        
        mQENBEx6nwkBCADuztv2tGhjPljwW46qEhth7ZnkdhYXuctZ6lNQuy5LMaEECE3C
        jvVKY+nBrgsLY2Trts+q+mdooBWvxy/qe5PAQTcPR83KjVS4fYwNMBgeRxBEZAZg
        DFwRRCsRrHost+cMgtzLocQ+vL3+9yTRAIe/WmYwbEDXg/c9JSC7kQbZqaAaOshO
        cIOyeB8/QoYee0fEnBzHMmcd0SB1YpwIvRG6v61lXmgpQ9CbovvXO6ZZyEyCX784
        9xprzqP1y03DPrbhuhBAY8EMf3KGJA1dEcU4+lbGEgmlOe2YSbWoLs7mRLFcq5xx
        JroYMtvXF04k4ZHNZAnT3IZc+lJyCqOp4vXpABEBAAG0HlRlc3R1c2VyIEQgPHRl
        c3RkQGV4YW1wbGUuY29tPokBOAQTAQIAIgUCTHqfCQIbAwYLCQgHAwIGFQgCCQoL
        BBYCAwECHgECF4AACgkQqZHi1Q/dNne/0wgApuPzh4J8p2quCK1ScsJHlgGRojGq
        IDPhZFtPn0p2IAkqr5sAhvZAjd3u9A2DqQ7pwOX7gnGRE7dSrK69IAjfbRMc5k16
        aBK2ADq2YgPEmTToots1A0Tj+LaCFOXYUtEkgAC+RfFIkCdt8z86GIr0kg19Q/vY
        I/LtvThAk28D8yIfDnW49Mc4GGq+qvrOytBaGu3dzW0mjYWGEyl0fdSjNqtKyWN7
        Qw70Kqysaoy1KiPRAgwiPQfMCEx6pVaXuAfgRKaJ18kCNOldpajLgQv6yeY7mhgu
        Q3Qe7xQlAtVObxskcTH2CWggl2dPqSMNieLK0g/ER8PIReGDCBXNSJ4qYbkBDQRM
        ep8JAQgAw/o1nhJPLGlIfEMzOGU0Jjj+DwEyB3QIEEc+WKRvgtGsJ4cbZdaGWBJq
        jSo7e9XC9jA2ih0+Gld0vWV7S0LZ84xXxQeadC+AZBFR+b9ga4aUFIji8Tdi2dWX
        QmY76hHIaF8rs6aJB7lRig735VRLxVHOb194t9KLUzZiEKqd71BvLQyuLqAfTEsT
        GRHgmydaxZbGXz+Z57jbQgm11CQEHX1dtS8uqWb64xrV5GAeuEhRj4R6Yiy7OPNi
        xXHxryH2Jd34pA0cGHYVcTgVjXuZ9FFP2SnXuxABONGAIaJuqg7ozYBa2kOdr0DN
        5Pxy5ocR7R2ZoN0pYD5+Cc7oGHjuCQARAQABiQEfBBgBAgAJBQJMep8JAhsMAAoJ
        EKmR4tUP3TZ369QIAKPlfX2TUfhP3otYiaa24zBJ/cvGljGiSfX0KrausBHH161j
        lraJfLzpe7vSOZhwZwgIY/eKoErAkJwVnX1+dLuOcHaqRDi5gnLqa6Yg9a2LWb4z
        rvgsvbiNUs1o9htOcvcpv7e3UUUcRa8lO+aNkO+VoI6DI8RJ3wIfJayboePRXdfr
        8g9of0jSdIOzlaaBPxA2wYSWXm4kv7QXzZooxuGqhn0+JKuq2+oO9y5QUig+c3oG
        a5mpVblmv5ZL6Gc36kCbeEC8j6JkNT4wnceQwpNUNYtPU186cjy3rAD4C58w0Uvp
        HZZSTc0syLOShQr//We39LUNaX6WF3NmyF8K/OM=
        =YDhQ
        -----END PGP PUBLIC KEY BLOCK-----
        """,
        """
        -----BEGIN PGP PRIVATE KEY BLOCK-----
        Version: GnuPG v1
        
        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
        =e1xT
        -----END PGP PRIVATE KEY BLOCK-----
        """);
  }

  /**
   * pub 2048R/4B4387EE 2010-08-29 [expired: 2011-08-29] Key fingerprint = F01D 677C 8BDB 854E 1054
   * 406E 3B09 B97F 4B43 87EE uid Testuser E &lt;teste@example.com&gt;
   */
  public static TestKey keyE() throws Exception {
    return new TestKey(
        """
        -----BEGIN PGP PUBLIC KEY BLOCK-----
        Version: GnuPG v1
        
        mQENBEx6nxoBCADjYOWOFa7ZBJpRuNspRoXBTK0LiK5zqN894b87LgIYEgUM6q5J
        yLNo43x7V+ow1/7BEq0JUAMSQ3uRn2jqXiJskSXvwlFYcTVFb0gY09CSD0ptHvda
        zqYOuM/MU1l9jqmlM+pDw/z0pLTKYmAHi6pKJ64pqccMHPUZHpLywyzSNX+JM86I
        K5KAsyGArtgpT9vfci3idNeXjhMR8rfLPDFbdGvGFOZrYv0cfgTbBpVEWeHjs2FR
        4vHG133AdjdZcvA9Y9VW34ZLeiyBEeFix7+HPVS82rko2kQxZu1UZRu340maKDAo
        +UVirgo0FQ8nNUR+c9oNKgiZtO39IAPJv/WZABEBAAG0HlRlc3R1c2VyIEUgPHRl
        c3RlQGV4YW1wbGUuY29tPokBPgQTAQIAKAUCTHqfGgIbAwUJAeEzgAYLCQgHAwIG
        FQgCCQoLBBYCAwECHgECF4AACgkQOwm5f0tDh+6Fowf9FZgntlW4qc7BHe8zYJ0q
        zoLZrHwCFcaeO3kz53y5Lz3+plMuqVDjoQDOt8DxsPHrXWKiu0qBTjZ28ztN3ef6
        f0MpguTGclvFroevUct0xiyox5r1DfMT8JRvqsojE1XPscR2zJzIgEg3OCPuksT9
        EsHsF+/3RBbsXbQgDpW38g0GzIJI4AiQ/yvG2ON9awN2kzIWoBkthVCGy54lCTGj
        yPhatE7Zu2ABNcerIDstupWww2Psec6pGbPPci8ojc90fzalk3UMXcXHD7m8cTJS
        kgHScOzTElIQqOA1+w6uiHy2oAn+qW7534j6p9Tj+DrSIzUXBedGjXZevaKaurVy
        KLkBDQRMep8aAQgAn5r6toYnEzwDeig8r+t89vqOFtohYcmtyeLeTiTTdAz/xWBW
        HUlqV8sglQ9aINpGtBf37v13RhtU3WkUv8cZMQoRM8P2H3cKDNwkucFO6uKSEQO5
        FdzTm4C4WaoE7QiTRbiekwh7O54mz4Wup6LHuEFQEcSpdRUp8w/qaJIHG9EJad1q
        UEsKNnITW+mWHY3+ccK1hgqPwOPqO3/8QtaipekKOYAtOb+57c1jtDFBZnYIkant
        oKs+kRw0DykXFTyFOMYqaleBMcVG+u7ljwAq18L8Ev+qVIpBIZ5eQ5+6p1w9B69h
        RH0Ebn50ebpoqKOXhN4/bu/wq596y0o4xDB0GQARAQABiQElBBgBAgAPBQJMep8a
        AhsMBQkB4TOAAAoJEDsJuX9LQ4fu0/wH/35/22xina8ktbvGV/kB0pH2LBqeXN/b
        CLdA+CDzfwMDzqG0kU39EJ3Fbux7fj4uMaeiYfbO9U85+NOuDmeH41B2dM9S1AzE
        H+/OiCp/Zf1fdd1qXhsA4Xe5vc/VD9oso9OrZK5CM5u0TPmYFijfVDPNgag6mPnD
        zd8JCsuEj4VEy6NF1KcoCc8edQ8AZ4L6ZQ6qiV24gxLnh8xImVr5YjBKDUCdrl79
        0u4wekfgapSx9Sw9Ycz5dFOL07OOHPiKZwUG0f8td6oJX4Ddxset5JAm1pPcLQHR
        6PRx0hI/Tz7rsAI6O37/BEM15+MVGIgOSLL/SRIpOa0L8qmuUhhS6Bg=
        =uA5x
        -----END PGP PUBLIC KEY BLOCK-----
        """,
        """
        -----BEGIN PGP PRIVATE KEY BLOCK-----
        Version: GnuPG v1
        
        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
        =HTKj
        -----END PGP PRIVATE KEY BLOCK-----
        """);
  }

  /**
   * pub 2048R/31FA48C4 2010-09-01 Key fingerprint = 85CE F045 8113 42DA 14A4 42AA 4A9F AC70 31FA
   * 48C4 uid Testuser F &lt;testf@example.com&gt; sub 2048R/50FF7D5C 2010-09-01
   */
  public static TestKey keyF() throws Exception {
    return new TestKey(
        """
        -----BEGIN PGP PUBLIC KEY BLOCK-----
        Version: GnuPG v1
        
        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
        =WDx2
        -----END PGP PUBLIC KEY BLOCK-----
        """,
        """
        -----BEGIN PGP PRIVATE KEY BLOCK-----
        Version: GnuPG v1
        
        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
        =ZLpl
        -----END PGP PRIVATE KEY BLOCK-----
        """);
  }

  /**
   * pub 2048R/E2D32BA5 2010-09-01 Key fingerprint = CB2B 665B 88DA D56A 7009 C15D 8A9B 5293 E2D3
   * 2BA5 uid Testuser G &lt;testg@example.com&gt; sub 2048R/829DAE8D 2010-09-01
   */
  public static TestKey keyG() throws Exception {
    return new TestKey(
        """
        -----BEGIN PGP PUBLIC KEY BLOCK-----
        Version: GnuPG v1
        
        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
        =34GE
        -----END PGP PUBLIC KEY BLOCK-----
        """,
        """
        -----BEGIN PGP PRIVATE KEY BLOCK-----
        Version: GnuPG v1
        
        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
        =T1WV
        -----END PGP PRIVATE KEY BLOCK-----
        """);
  }

  /**
   * pub 2048R/080E5723 2010-09-01 Key fingerprint = 2957 ABE4 937D A84A 2E5D 31DB 65C4 33C4 080E
   * 5723 uid Testuser H &lt;testh@example.com&gt; sub 2048R/68C7C262 2010-09-01
   */
  public static TestKey keyH() throws Exception {
    return new TestKey(
        """
        -----BEGIN PGP PUBLIC KEY BLOCK-----
        Version: GnuPG v1
        
        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
        =lddL
        -----END PGP PUBLIC KEY BLOCK-----
        """,
        """
        -----BEGIN PGP PRIVATE KEY BLOCK-----
        Version: GnuPG v1
        
        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
        =voB9
        -----END PGP PRIVATE KEY BLOCK-----
        """);
  }

  /**
   * pub 2048R/5E9AEDD0 2010-09-01 Key fingerprint = 818D 5D0B 4AE2 A4FE A4C3 C44D D022 DA3A 5E9A
   * EDD0 uid Testuser I &lt;testi@example.com&gt; sub 2048R/0884E452 2010-09-01
   */
  public static TestKey keyI() throws Exception {
    return new TestKey(
        """
        -----BEGIN PGP PUBLIC KEY BLOCK-----
        Version: GnuPG v1
        
        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
        =SiG3
        -----END PGP PUBLIC KEY BLOCK-----
        """,
        """
        -----BEGIN PGP PRIVATE KEY BLOCK-----
        Version: GnuPG v1
        
        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
        =RcWw
        -----END PGP PRIVATE KEY BLOCK-----
        """);
  }

  /**
   * pub 2048R/C2E6A198 2010-09-01 Key fingerprint = 83AB CE4D 6845 D6DA F7FB AA47 A139 3C46 C2E6
   * A198 uid Testuser J &lt;testj@example.com&gt; sub 2048R/863E8ABF 2010-09-01
   */
  public static TestKey keyJ() throws Exception {
    return new TestKey(
        """
        -----BEGIN PGP PUBLIC KEY BLOCK-----
        Version: GnuPG v1
        
        mQENBEx+aUgBCADczNio9UWnUggUZkdqJye57497oD9vNo9rmtR+i1TkMpVeWaMH
        UWrm1twzIPV9D4lAWLJoG2cYF6nXG1JxsKc9mOIZ6O1WfsopMUU0p+EfU8H/cvdM
        /iccYS6OnNL4/xR1R7hlA4+b/jaOZfzdS3i5jwOf+TtCk7c5qOuFhraiVQ9H1G86
        +LsiWVeXEFc/FXxnESRmbaZFNJrAdJl23eKXRC6az0S5FwMVBvUhRpLwIGDbVT/0
        /QwaNUOq3bYwudPREFLg/1HtBuxNhRdV6mCrit+tsPan9o0/WtsHuq8n4/pqOKBc
        RRmOIQR9SEohE2TuVT3XVFpMXa4a4CBuNXjTABEBAAG0HlRlc3R1c2VyIEogPHRl
        c3RqQGV4YW1wbGUuY29tPokBOAQTAQIAIgUCTH5pSAIbAwYLCQgHAwIGFQgCCQoL
        BBYCAwECHgECF4AACgkQoTk8RsLmoZi0BggAlnbCwmwaLwcpU9YcOE9/8KF56dIs
        XhdxzqdP91UmhVT0df1OBhgTqFkKprBLCT+B9yBClsnyXMatkvuhQG6C7lw9toMO
        TITRPZoFJe3Ezi+HRRPqAPubIcSgeILuilvFhkoUOgoC1ubmVPgcGBLb8tdvI3bA
        svq+n2jaYUlgL5N6ZNRNakc07e8vH5SeKiD8ZntJlTU49fkxzlawtDaI3+GhyUiB
        0Ah8pl143DFNAq8CfvQCPKwX4WFPkEflh0LlgaEPJUZ/H6KxKXXF8SC9cD2VIii8
        Yrue8y9T+j5y699A0GCptb1IKrgxbfhgD//3g3l1eXsEwn2cwFNCt7pZFLkBDQRM
        fmlIAQgA3E2pM6oDJGgfxbqSfykuRtTbiAi7JEd1DNvEe6gJ7qkBLM4ipILBD0qR
        qCwL37E4/3nMsZjA7GIFLQj2DrFW3aEEKwR/zdh7R67lo9CunCY+FPWTuOkCG8Sh
        3RLpbAV6I61NG/wDznW30vmKNJDgPpkzYj8u0T4MtpywEgxTxCqWZKCufWDRfNAy
        IBLt+piG+bcYKfw9pS8PvXPQMNIi4U2pu3hb/BHC3Y1A8FVpEe4CFV7rWb/K2Ydx
        eBxwwxm9sBxF+vhlI+ZEeb9JxGH6jYlc6twD4e6p3KqusAKLKiLsS5uLQnpMGGZ8
        vcpTSfyHjG2QHc3qG9S/yDCZjhhe2QARAQABiQEfBBgBAgAJBQJMfmlIAhsMAAoJ
        EKE5PEbC5qGYClMIANTdZ+/g/FPl1Lm0tO1CSnHVHekeGNA9n3L6SGiSZQJjEDo0
        gsye5xgxh5JGKf7CqbEFfeLC9Jx5W5EN4dVFudncIlC/SutfRzdt5W8CLXMl0c41
        5FmtpWNStk3MglkcjE5PrRRrSiRc45S0e2kIPb8eiVKg98/rCToC9+Qn3pMi/fcM
        LVpYE+dhvB5EhOSwBWWgvWXzeLhv5CnBKxH0ItHhNwvt8qPOHgQAJKPc6dV888xn
        Sew62LFefHPnGTHLP8RRgVIvZBG5IoovxSz89QGHQZiC4xv00I7zNwmtr6eEcl+y
        BkUK9QWITEBHUDqR+cbVa2dRr3fUHwRP7G2G+ow=
        =ucAX
        -----END PGP PUBLIC KEY BLOCK-----
        """,
        """
        -----BEGIN PGP PRIVATE KEY BLOCK-----
        Version: GnuPG v1
        
        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
        =NiQI
        -----END PGP PRIVATE KEY BLOCK-----
        """);
  }

  private TestTrustKeys() {}
}
