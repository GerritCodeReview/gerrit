import re
from collections import defaultdict

def parse_test_output(log_file):
    with open(log_file, 'r') as f:
        content = f.read()

    errors = defaultdict(list)
    
    # This regex tries to find blocks of logs for each test file.
    # It looks for a line starting with 'app/' and ending with '_test.ts:', 
    # and captures everything until the next similar line or end of the string.
    pattern = re.compile(r'(app/.*?_test\.ts:.*?)(?=app/.*?_test\.ts:|\Z)', re.DOTALL)
    
    for match in pattern.finditer(content):
        block = match.group(1)
        lines = block.split('\n')
        
        # The first line of the block is the file path
        file_path = lines[0].strip(':')
        
        in_browser_log = False
        for line in lines[1:]:
            line = line.strip()
            if '🚧 Browser logs:' in line:
                in_browser_log = True
                continue
            
            if in_browser_log:
                if not line or line.startswith('gr-') or line.startswith('Running tests') or ' passed, ' in line or line.startswith('test '):
                    in_browser_log = False
                    continue

                if 'An error was thrown in a Promise' in line or \
                   'AssertionError' in line or \
                   line.startswith('{') or \
                   line.startswith('}') or \
                   line.startswith('stack:') or \
                   line.startswith('at ') or \
                   re.match(r'^\s*at ', line) or \
                   'did you forget to await' in line.lower() :
                    continue

                clean_line = re.sub(r'\x1b\[[0-9;]*m', '', line)
                clean_line = re.sub(r'http://localhost:[0-9]+', '', clean_line)
                clean_line = clean_line.strip()

                if clean_line:
                    errors[clean_line].append(file_path)
    return errors

def format_output(errors):
    output = []
    for category, files in sorted(errors.items(), key=lambda item: (len(item[1]), item[0]), reverse=True):
        unique_files = sorted(list(set(files)))
        files_str = ', '.join(unique_files)
        if len(files_str) > 250:
             files_str = files_str[:250] + '... [truncated]'
        
        output.append(f"Category: {category}")
        output.append(f"Repeated: {len(files)}")
        output.append(f"Prompt: The following test(s) produced this error: {files_str}. This should be fixed.")
        output.append("")
    return "\n".join(output)

if __name__ == '__main__':
    errors = parse_test_output('test.output')
    formatted_output = format_output(errors)
    with open('test-category.output.new', 'w') as f:
        f.write(formatted_output)