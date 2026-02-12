# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import json
import os

import requests

def upload_artifact(name, file):

    with open(os.environ['LUCI_CONTEXT']) as f:
        sink = json.load(f)['result_sink']

        artifacts = {
            name: {
                'filePath': file,
            }
        }
        print("Request JSON:")
        print(artifacts)

        url = 'http://%s/prpc/luci.resultsink.v1.Sink/ReportInvocationLevelArtifacts' % (sink['address'])
        print("URL:")
        print(url)

        with requests.Session() as session:
            session.headers = {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
                'Authorization': 'ResultSink %s' % sink['auth_token'],
            }

            session.post(url, json={'artifacts': artifacts}).raise_for_status()