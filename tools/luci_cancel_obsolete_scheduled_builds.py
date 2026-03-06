#!/usr/bin/env python3
# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import subprocess
import sys
import time

BUILDER_NAME_PREFIX = 'Tag: scheduler_job_id:r8/'
COMMIT_HASH_PREFIX = 'Commit: https://r8.googlesource.com/r8/+/'
COMMIT_HASH_SUFFIX = ' on refs/heads/main'


def cancel_build(scheduled_build, builder_name, commit_hash):
    build_id = get_build_id_from_scheduled_build(scheduled_build)
    print(f'Cancelling build {builder_name}/{build_id} ({commit_hash})')
    subprocess.check_output([
        'bb', 'cancel', '-reason',
        'Automatically cancelled since scheduled build is obsolete',
        f'r8/ci/{builder_name}/{build_id}'
    ])


# Read in lines on the form:
# http://cr-buildbucket.appspot.com/build/8688163185568436321 SCHEDULED 'r8/ci/linux-android-4.4/1450'
# Created today at 14:55:35, waiting for 2m1s,
# By: project:r8
# Commit: https://r8.googlesource.com/r8/+/4ebfa384241001d599cc1631d735f0092bc1b91b on refs/heads/main
# Tag: buildset:commit/gitiles/r8.googlesource.com/r8/+/4ebfa384241001d599cc1631d735f0092bc1b91b
# Tag: scheduler_invocation_id:8853593247469195040
# Tag: scheduler_job_id:r8/linux-android-4.4
# Tag: user_agent:luci-scheduler
def get_scheduled_builds():
    stdout = subprocess.check_output(
        ['bb', 'ls', '-status', 'scheduled', 'r8/ci']).decode('UTF-8')
    result = []
    for line in stdout.splitlines():
        line = line.strip()
        if not line:
            continue
        if ' SCHEDULED \'r8/ci/' in line:
            result.append([])
        if result:
            result[-1].append(line)
        else:
            raise Exception('Unexpected line: ' + line)
    return result


# Extract from the line:
# http://cr-buildbucket.appspot.com/build/8688163185568436321 SCHEDULED 'r8/ci/linux-android-4.4/1450'
def get_build_id_from_scheduled_build(scheduled_build):
    header_line = scheduled_build[0]
    if ' SCHEDULED \'r8/ci/' not in header_line or not header_line.endswith(
            '\''):
        raise Exception('Unexpected header line: ' + header_line)
    separator_index = header_line.rindex('/')
    if separator_index < 0:
        raise Exception('Unexpected header line: ' + header_line)
    build_id = header_line[separator_index + 1:-1]
    if not build_id.isdigit():
        raise Exception('Unexpected build id: ' + build_id)
    return build_id


# Extract from the line:
# Tag: scheduler_job_id:r8/linux-android-14
def get_builder_name_from_scheduled_build(scheduled_build):
    for line in scheduled_build:
        if not line.startswith(BUILDER_NAME_PREFIX):
            continue
        builder_name = line[len(BUILDER_NAME_PREFIX):]
        return builder_name
    raise Exception(
        f'Expected to find line "{BUILDER_NAME_PREFIX}<builder_name>"')


# Extract from the line:
# Commit: https://r8.googlesource.com/r8/+/<hash> on refs/heads/main
def get_commit_hash_from_scheduled_build(scheduled_build):
    for line in scheduled_build:
        if not line.startswith(COMMIT_HASH_PREFIX):
            continue
        if not line.endswith(COMMIT_HASH_SUFFIX):
            continue
        commit_hash = line[len(COMMIT_HASH_PREFIX):-len(COMMIT_HASH_SUFFIX):]
        if len(commit_hash) != 40:
            raise Exception(
                'Expected commit hash to be 40 characters, but was: ' +
                commit_hash)
        return commit_hash
    raise Exception(
        f'Expected to find line "{COMMIT_HASH_PREFIX}<hash>{COMMIT_HASH_SUFFIX}"'
    )


def get_latest_commit_hash():
    subprocess.check_output(['git', 'fetch', 'origin'])
    stdout = subprocess.check_output(
        ['git', 'log', '-n', '1', 'origin/main',
         '--pretty=format:%H']).decode('UTF-8')
    return stdout.strip()


def is_cancelation_enabled_for_builder(builder_name):
    return builder_name.startswith(
        'linux') and not builder_name.endswith('_release')


def main(argv):
    while True:
        latest_commit_hash = get_latest_commit_hash()
        for scheduled_build in get_scheduled_builds():
            builder_name = get_builder_name_from_scheduled_build(
                scheduled_build)
            commit_hash = get_commit_hash_from_scheduled_build(scheduled_build)
            if commit_hash != latest_commit_hash and is_cancelation_enabled_for_builder(
                    builder_name):
                cancel_build(scheduled_build, builder_name, commit_hash)
        time.sleep(5 * 60 * 1000)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
