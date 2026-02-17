// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

(function() {
  const protoSchema = document.getElementById('blastradius-proto').textContent;
  const summariesData = JSON.parse(document.getElementById('blastradius-data').textContent);
  const tableBody = document.getElementById('summary-table-body');
  const headers = document.querySelectorAll('th[data-sort]');

  const root = protobuf.parse(protoSchema).root;
  const BlastRadiusSummary = root.lookupType("com.android.tools.r8.blastradius.proto.BlastRadiusSummary");

  let summaries = [];
  let currentSort = {
    key: null,
    desc: true
  };

  summariesData.forEach(summaryBase64 => {
    try {
      const binary = atob(summaryBase64);
      const bytes = new Uint8Array(binary.length);
      for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
      }
      const message = BlastRadiusSummary.decode(bytes);
      const summary = BlastRadiusSummary.toObject(message, {
        defaults: true,
        enums: String
      });
      summaries.push(summary);
    } catch (e) {
      console.error("Failed to decode summary:", e);
    }
  });

  function renderTable() {
    tableBody.innerHTML = '';
    summaries.forEach(renderRow);
  }

  function renderRow(summary) {
    // Name row (Full width)
    const nameTr = document.createElement('tr');
    nameTr.className = 'name-row';
    const nameTd = document.createElement('td');
    nameTd.colSpan = 6;
    const link = document.createElement('a');
    link.className = 'report-link';
    link.href = summary.link || summary.name.replace('.pb', '.html');
    link.textContent = summary.name;
    nameTd.appendChild(link);
    nameTr.appendChild(nameTd);
    tableBody.appendChild(nameTr);

    // Data row
    const dataTr = document.createElement('tr');
    dataTr.className = 'data-row';

    const rulesTd = document.createElement('td');
    rulesTd.textContent = summary.keepRuleCount || "0";
    if (summary.keepRulePackageWideCount && summary.keepRulePackageWideCount !== 0) {
      const span = document.createElement('span');
      span.className = 'stat-badge warn';
      span.textContent = `${summary.keepRulePackageWideCount} package-wide`;
      span.title = "Number of keep rules that keep an entire package";
      rulesTd.appendChild(document.createElement('br'));
      rulesTd.appendChild(span);
    }
    dataTr.appendChild(rulesTd);

    const itemsTd = document.createElement('td');
    const totalKept = (summary.keptClasses?.itemCount || 0) +
      (summary.keptMethods?.itemCount || 0) +
      (summary.keptFields?.itemCount || 0);
    const totalItems = (summary.classCount || 0) + (summary.methodCount || 0) + (summary.fieldCount || 0);
    itemsTd.textContent = `${totalKept} / ${totalItems}`;
    dataTr.appendChild(itemsTd);

    const classesTd = document.createElement('td');
    classesTd.innerHTML = formatKeepInfo(summary.keptClasses, summary.classCount);
    dataTr.appendChild(classesTd);

    const methodsTd = document.createElement('td');
    methodsTd.innerHTML = formatKeepInfo(summary.keptMethods, summary.methodCount);
    dataTr.appendChild(methodsTd);

    const fieldsTd = document.createElement('td');
    fieldsTd.innerHTML = formatKeepInfo(summary.keptFields, summary.fieldCount);
    dataTr.appendChild(fieldsTd);

    const problematicTd = document.createElement('td');
    if (summary.keepRuleBlastRadius && summary.keepRuleBlastRadius.length > 0) {
      const ul = document.createElement('ul');
      ul.style.margin = '0';
      ul.style.paddingLeft = '0';
      ul.style.listStyleType = 'none';
      ul.style.fontSize = '0.9rem';
      summary.keepRuleBlastRadius.forEach(rule => {
        const li = document.createElement('li');
        li.style.marginBottom = '4px';

        const badge = document.createElement('span');
        badge.className = 'stat-badge warn';
        badge.textContent = rule.itemCount;
        badge.title = "Number of items kept by the rule.";
        badge.style.marginRight = '8px';

        li.appendChild(badge);
        const code = document.createElement('span');
        code.style.fontFamily = 'monospace';
        code.style.fontSize = '0.85rem';
        code.textContent = rule.source;
        li.appendChild(code);
        ul.appendChild(li);
      });
      problematicTd.appendChild(ul);
    } else {
      problematicTd.textContent = "-";
    }
    dataTr.appendChild(problematicTd);

    tableBody.appendChild(dataTr);
  }

  function formatKeepInfo(keepInfo, total) {
    if (!keepInfo) return "0 / " + (total || 0);
    const kept = keepInfo.itemCount || 0;
    let badges = "";
    if (keepInfo.noObfuscationCount > 0) {
      badges += `<span class="stat-badge" title="No obfuscation">${keepInfo.noObfuscationCount || 0} OBF</span>`;
    }
    if (keepInfo.noOptimizationCount > 0) {
      badges += `<span class="stat-badge warn" title="No optimization">${keepInfo.noOptimizationCount || 0} OPT</span>`;
    }
    if (keepInfo.noShrinkingCount > 0) {
      badges += `<span class="stat-badge error" title="No shrinking">${keepInfo.noShrinkingCount || 0} SHR</span>`;
    }
    return `${kept} / ${total || 0}<br>${badges}`;
  }

  function sortSummaries(key) {
    if (currentSort.key === key) {
      currentSort.desc = !currentSort.desc;
    } else {
      currentSort.key = key;
      currentSort.desc = true;
    }

    summaries.sort((a, b) => {
      let valA, valB;
      switch (key) {
        case 'rules':
          valA = a.keepRuleCount || 0;
          valB = b.keepRuleCount || 0;
          break;
        case 'items':
          valA = (a.keptClasses?.itemCount || 0) + (a.keptMethods?.itemCount || 0) + (a.keptFields?.itemCount || 0);
          valB = (b.keptClasses?.itemCount || 0) + (b.keptMethods?.itemCount || 0) + (b.keptFields?.itemCount || 0);
          break;
        case 'classes':
          valA = (a.keptClasses && a.keptClasses.itemCount) || 0;
          valB = (b.keptClasses && b.keptClasses.itemCount) || 0;
          break;
        case 'methods':
          valA = (a.keptMethods && a.keptMethods.itemCount) || 0;
          valB = (b.keptMethods && b.keptMethods.itemCount) || 0;
          break;
        case 'fields':
          valA = (a.keptFields && a.keptFields.itemCount) || 0;
          valB = (b.keptFields && b.keptFields.itemCount) || 0;
          break;
        default:
          return 0;
      }
      return currentSort.desc ? valB - valA : valA - valB;
    });

    renderTable();
    updateHeaderStyles();
  }

  function updateHeaderStyles() {
    headers.forEach(th => {
      const key = th.getAttribute('data-sort');
      th.innerHTML = th.textContent.replace(/ [▲▼]/, '');
      if (key === currentSort.key) {
        th.innerHTML += currentSort.desc ? ' ▼' : ' ▲';
      }
    });
  }

  headers.forEach(th => {
    th.addEventListener('click', () => {
      sortSummaries(th.getAttribute('data-sort'));
    });
  });

  sortSummaries('items');
})();