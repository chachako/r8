#!/usr/bin/env python3
# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import base64
import os

# Paths relative to this script
web_dir = os.path.dirname(os.path.abspath(__file__))
index_path = os.path.join(web_dir, 'index.html')
style_path = os.path.join(web_dir, 'style.css')
main_js_path = os.path.join(web_dir, 'main.js')
proto_path = os.path.join(web_dir, 'blastradius.proto')


def ParseOptions():
    parser = argparse.ArgumentParser(
        description='Create a standalone Blast Radius report.')
    parser.add_argument('input',
                        help='Input .pb file or directory containing .pb files')
    parser.add_argument('--output', help='Output .html file')
    options = parser.parse_args()
    if os.path.isdir(options.input) and options.output:
        raise Exception('Argument --output is not supported when passing a dir')
    return options


def readTextFile(path):
    with open(path, 'r') as f:
        return f.read()


def readBinaryFile(path):
    with open(path, 'rb') as f:
        return base64.b64encode(f.read()).decode('utf-8')


def writeHtml(input, output):
    # Read HTML.
    html = readTextFile(index_path)

    # Inline CSS
    html = html.replace('<link rel="stylesheet" href="style.css">',
                        f'<style>\n{readTextFile(style_path)}\n</style>')

    # Embed proto schema, proto data, and main.js.
    embedded_data = f"""
<script id="blastradius-proto" type="text/plain">
{readTextFile(proto_path)}
</script>
<script id="blastradius-data" type="application/octet-stream">
{readBinaryFile(input)}
</script>
<script>
{readTextFile(main_js_path)}
</script>
"""
    html = html.replace('<script src="main.js"></script>', embedded_data)

    # Write output.
    with open(output, 'w') as f:
        f.write(html)


def main():
    options = ParseOptions()

    # Check if files exist
    for path in [index_path, style_path, main_js_path, proto_path]:
        if not os.path.exists(path):
            print(f'Error: Required file not found: {path}')
            return 1

    if os.path.isdir(options.input):
        for root, dirs, files in os.walk(options.input):
            for file in files:
                if file.endswith('.pb') and 'blastradius' in os.path.basename(
                        file):
                    pb_path = os.path.join(root, file)
                    html_path = pb_path.replace('.pb', '.html')
                    writeHtml(pb_path, html_path)
    else:
        writeHtml(options.input, options.output or 'blastradius.html')
    return 0


if __name__ == '__main__':
    exit(main())
