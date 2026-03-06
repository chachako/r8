#!/usr/bin/env python3
# Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.
import argparse
import sys
import subprocess
import json
import gradle
import utils
import os
import shutil
import perf
import csv


def get_profiler_executable():
    profiler_path = os.path.join(utils.THIRD_PARTY, 'gradle-profiler',
                                 'gradle-profiler-0.23.0', 'bin')
    if utils.IsWindows():
        return os.path.join(profiler_path, 'gradle-profiler.bat')
    else:
        return os.path.join(profiler_path, 'gradle-profiler')


GRADLE_PROFILER_SHA1 = os.path.join(utils.THIRD_PARTY,
                                    'gradle-profiler.tar.gz.sha1')
GRADLE_PROFILER_TGZ = os.path.join(utils.THIRD_PARTY, 'gradle-profiler.tar.gz')


def ensure_deps():
    gradle.ensure_deps()
    utils.EnsureDepFromGoogleCloudStorage(get_profiler_executable(),
                                          GRADLE_PROFILER_TGZ,
                                          GRADLE_PROFILER_SHA1, 'Gradle binary')


def run_gradle_profiler(cwd, benchmark_name, scenario_file, local_output_dir,
                        tmp_gradle_home, just_once, run_specific,
                        throw_on_failure):
    cmd = [get_profiler_executable(), '--benchmark']
    # Title in html report.
    cmd.extend(['--title', benchmark_name])
    # Point to gradle executable.
    cmd.extend(['--gradle-version', gradle.get_gradle_dir()])
    # Point to temporary gradle home (to not use local gradle properties).
    cmd.extend(['--gradle-user-home', tmp_gradle_home])
    # Overwrite scenario benchmark loop count if set.
    if just_once:
        # The profiler requires warmups >= 1.
        cmd.extend(['--warmups', '1'])
        cmd.extend(['--iterations', '1'])
    # Set output directory.
    cmd.extend(['--output-dir', local_output_dir])
    # Point to benchmark scenarios.
    cmd.extend(['--scenario-file', scenario_file])
    # Either run the performance group or the specified scenario.
    if run_specific:
        cmd.extend([run_specific])
    else:
        cmd.extend(['--group', 'performance-suite'])
    utils.PrintCmd(cmd)
    with utils.ChangedWorkingDirectory(cwd):
        return_value = subprocess.call(cmd, env=gradle.get_java_env(None))
    if throw_on_failure and return_value != 0:
        raise Exception('failed to run gradle benchmarks')
    return return_value


def parse_options():
    parser = argparse.ArgumentParser(description='Benchmark gradle.')
    parser.add_argument('--just-once',
                        help='Only run each benchmark once.',
                        default=False,
                        action='store_true')
    parser.add_argument('--scenario',
                        help='Run a specific benchmark scenario.',
                        default=None)
    parser.add_argument('--upload-benchmark-data-to-google-storage',
                        help='Uploads the benchmark data.',
                        default=False,
                        action='store_true')
    parser.add_argument(
        '--skip-benchmarks',
        help='Skips the benchmarking but performs uploading and so on.',
        default=False,
        action='store_true')
    parser.add_argument(
        '--output-dir',
        help='If upload is enabled, the output is instead stored here.',
        default=None)
    parser.add_argument(
        '--profiler-output-dir',
        help=
        'stores the immediate output of gradle-profiler in the given directory.',
        default=None)
    return parser.parse_args()


# Upload to google storage unless a outdir is specified.
def upload_benchmark(csv_path, log_path, outdir=None):
    benchmark_data = read_benchmark_csv(csv_path)
    for runs in benchmark_data:
        upload_run(log_path, runs['benchmark'], runs['warmups'],
                   runs['iterations'], outdir)


# Upload to google storage unless a outdir is specified.
def upload_run(log_path, benchmark_name, warmups, iterations, outdir):
    with utils.TempDir() as temp:
        output_file = os.path.join(temp, 'result.json')
        target_name = "build"
        # Write benchmark results.
        write_benchmark_output_to_file(output_file, benchmark_name, warmups,
                                       iterations)
        benchmark_dest = perf.GetArtifactLocation(benchmark=benchmark_name,
                                                  target=target_name,
                                                  version=None,
                                                  filename="result.json")
        perf.ArchiveOutputFile(output_file, benchmark_dest, outdir=outdir)

        # Write log.
        log_dest = perf.GetArtifactLocation(benchmark=benchmark_name,
                                            target=target_name,
                                            version=None,
                                            filename="log.txt")
        perf.ArchiveOutputFile(log_path, log_dest, outdir=outdir)

        # Write metadata.
        if utils.is_bot():
            meta_file = os.path.join(temp, "meta")
            with open(meta_file, 'w') as f:
                f.write("Produced by: " + os.environ.get('SWARMING_BOT_ID'))
            meta_dest = perf.GetArtifactLocation(benchmark=benchmark_name,
                                                 target=target_name,
                                                 version=None,
                                                 filename='meta')
            perf.ArchiveOutputFile(meta_file, meta_dest, outdir=outdir)


# The csv file looks something like this
# (note that measured build columns can be missing data):
#     scenario,configuration,rerun_main_compile_java
#     version,Gradle 8.12.1,Gradle 8.12.1
#     tasks,default tasks,:main:compileJava
#     value,total execution time,total execution time
#     warm-up build #1,16485.13,110125.02
#     warm-up build #2,3406.78,4113.01
#     measured build #1,2785.97,3464.33
#     measured build #2,2758.61,3278.68
#     measured build #3,,3831.12
def read_benchmark_csv(csv_path):
    with open(csv_path, 'r') as f:
        reader = csv.DictReader(f)
        scenarios = [h for h in reader.fieldnames if h != 'scenario']
        data = {s: {'warmups': [], 'iterations': []} for s in scenarios}
        for row in reader:
            tag = row['scenario']
            for s in scenarios:
                val = row[s]
                if not val:
                    continue
                try:
                    num_val = float(val)
                except ValueError:
                    continue
                if 'warm-up' in tag:
                    data[s]['warmups'].append(num_val)
                elif 'measured' in tag:
                    data[s]['iterations'].append(num_val)

        return [{
            'benchmark': s,
            'warmups': data[s]['warmups'],
            'iterations': data[s]['iterations']
        } for s in scenarios]


def write_benchmark_output_to_file(path, benchmark_name, warmups, iterations):
    with open(path, 'w') as f:
        json.dump(
            {
                'benchmark_name':
                    benchmark_name,
                'results': [{
                    'runtime': convert_ms_to_ns(time)
                } for time in iterations],
                'warmup': [{
                    'runtime': convert_ms_to_ns(time)
                } for time in warmups]
            }, f)


def convert_ms_to_ns(time):
    return int(time * 1_000_000)


# Stores the immediate profiler output in profiler_output_dir.
def run_gradle_profiler_with_output_dir(args, profiler_output_dir):
    with utils.TempDir() as temp_gradle_home:
        csv_path = os.path.join(profiler_output_dir, 'benchmark.csv')
        log_path = os.path.join(profiler_output_dir, 'profile.log')
        if not args.skip_benchmarks:
            ensure_deps()
            run_gradle_profiler(cwd=utils.REPO_ROOT,
                                benchmark_name='R8 build benchmarks',
                                scenario_file=os.path.join(
                                    utils.TOOLS_DIR,
                                    'gradle_benchmark.scenarios'),
                                local_output_dir=profiler_output_dir,
                                tmp_gradle_home=temp_gradle_home,
                                just_once=args.just_once,
                                run_specific=args.scenario,
                                throw_on_failure=True)
        if args.upload_benchmark_data_to_google_storage:
            upload_benchmark(csv_path, log_path, args.output_dir)


def main():
    args = parse_options()
    if args.profiler_output_dir:
        run_gradle_profiler_with_output_dir(args, args.profiler_output_dir)
    else:
        with utils.TempDir() as temp_output_dir:
            run_gradle_profiler_with_output_dir(args, temp_output_dir)
    return 0


if __name__ == '__main__':
    sys.exit(main())
