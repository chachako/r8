// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
let protobufRoot = null;
let containerData = null;
let filteredRules = [];
let filteredFiles = [];
let currentView = 'rules'; // 'rules', 'files', 'unused', 'redundant'
let currentSubView = 'class'; // 'class', 'method', 'field'
let currentSelectedRule = null;
let currentRulePagination = {
  classes: 100,
  methods: 100,
  fields: 100
};

// Lookup maps
let tables = {
  rules: new Map(),
  classes: new Map(),
  fields: new Map(),
  methods: new Map(),
  types: new Map(),
  protos: new Map(),
  typeLists: new Map(),
  origins: new Map(),
  jarOrigins: new Map(),
  keepConstraints: new Map(),
  files: new Map(), // filename -> { rules: [], totalRadius: 0, classes: 0, methods: 0, fields: 0 }
  methodRefs: new Map(),
  fieldRefs: new Map()
};

let constraintStats = {
  DONT_OBFUSCATE: {
    classes: new Set(),
    methods: new Set(),
    fields: new Set()
  },
  DONT_OPTIMIZE: {
    classes: new Set(),
    methods: new Set(),
    fields: new Set()
  },
  DONT_SHRINK: {
    classes: new Set(),
    methods: new Set(),
    fields: new Set()
  }
};

const dropZone = document.getElementById('drop-zone');
const fileInput = document.getElementById('file-input');
const ruleList = document.getElementById('rule-list');
const searchInput = document.getElementById('search-input');
const mainContent = document.getElementById('main-content');

const embeddedProtoSchemaSource = document.getElementById('blastradius-proto');
const embeddedProtoDataSource = document.getElementById('blastradius-data');

// Load Protobuf definition.
if (embeddedProtoSchemaSource) {
  try {
    protobufRoot = protobuf.parse(embeddedProtoSchemaSource.textContent).root;
  } catch (e) {
    console.error("Failed to parse embedded proto:", e);
  }
} else {
  protobuf.load("blastradius.proto", function(err, root) {
    if (err) {
      console.error("Error loading proto:", err);
      alert("Failed to load blastradius.proto.");
      return;
    }
    protobufRoot = root;
  });
}

// Load blast radius data (.pb).
if (embeddedProtoDataSource) {
  setTimeout(() => {
    try {
      const data = embeddedProtoDataSource.textContent.trim();
      const bytes = Uint8Array.from(atob(data), c => c.charCodeAt(0));
      const BlastRadiusContainer = protobufRoot.lookupType("com.android.tools.r8.blastradius.proto.BlastRadiusContainer");
      const message = BlastRadiusContainer.decode(bytes);
      containerData = BlastRadiusContainer.toObject(message, {
        longs: String,
        enums: String,
        bytes: String,
        defaults: true,
        arrays: true,
        objects: true,
        oneofs: true
      });
      processData();
    } catch (e) {
      console.error("Failed to decode embedded data:", e);
    }
  }, 0);
} else {
  dropZone.addEventListener('click', () => fileInput.click());

  dropZone.addEventListener('dragover', (e) => {
    e.preventDefault();
    dropZone.classList.add('hover');
  });

  dropZone.addEventListener('dragleave', () => {
    dropZone.classList.remove('hover');
  });

  dropZone.addEventListener('drop', (e) => {
    e.preventDefault();
    dropZone.classList.remove('hover');
    if (e.dataTransfer.files.length > 0) {
      handleFile(e.dataTransfer.files[0]);
    }
  });

  fileInput.addEventListener('change', (e) => {
    if (e.target.files.length > 0) {
      handleFile(e.target.files[0]);
    }
  });

  function handleFile(file) {
    if (!protobufRoot) {
      alert("Protobuf definition not loaded yet.");
      return;
    }

    renderLoading();
    const reader = new FileReader();
    reader.onload = function(e) {
      const arrayBuffer = e.target.result;
      setTimeout(() => {
        try {
          const BlastRadiusContainer = protobufRoot.lookupType("com.android.tools.r8.blastradius.proto.BlastRadiusContainer");
          const buffer = new Uint8Array(arrayBuffer);
          const message = BlastRadiusContainer.decode(buffer);
          containerData = BlastRadiusContainer.toObject(message, {
            longs: String,
            enums: String,
            bytes: String,
            defaults: true,
            arrays: true,
            objects: true,
            oneofs: true
          });
          processData();
        } catch (err) {
          console.error("Error decoding proto:", err);
          alert("Failed to decode the .pb file. Ensure it matches the blastradius.proto schema.");
          renderWelcomeMessage();
          dropZone.classList.remove('hidden');
        }
      }, 0);
    };
    reader.readAsArrayBuffer(file);
  }
}

searchInput.addEventListener('input', (e) => {
  if (currentView === 'rules') {
    filterRules(e.target.value);
  } else if (currentView === 'unused') {
    filterUnusedRules(e.target.value);
  } else if (currentView === 'redundant') {
    filterRedundantRules(e.target.value);
  } else if (currentView === 'files') {
    filterFiles(e.target.value);
  }
});

function processData() {
  // Clear previous data
  Object.values(tables).forEach(map => map instanceof Map ? map.clear() : null);
  tables.files = new Map();

  // Reset constraint stats
  Object.values(constraintStats).forEach(s => {
    s.classes.clear();
    s.methods.clear();
    s.fields.clear();
  });

  // Build lookup tables for origins
  if (containerData.classFileInJarOriginTable)
    containerData.classFileInJarOriginTable.forEach(o => tables.jarOrigins.set(o.id, o));
  if (containerData.fileOriginTable)
    containerData.fileOriginTable.forEach(o => tables.origins.set(o.id, o));

  // Build lookup table for references.
  if (containerData.fieldReferenceTable)
    containerData.fieldReferenceTable.forEach(f => tables.fieldRefs.set(f.id, f));
  if (containerData.methodReferenceTable)
    containerData.methodReferenceTable.forEach(m => tables.methodRefs.set(m.id, m));
  if (containerData.protoReferenceTable)
    containerData.protoReferenceTable.forEach(p => tables.protos.set(p.id, p));
  if (containerData.typeReferenceTable)
    containerData.typeReferenceTable.forEach(t => tables.types.set(t.id, t));
  if (containerData.typeReferenceListTable)
    containerData.typeReferenceListTable.forEach(tl => tables.typeLists.set(tl.id, tl));

  // Build lookup tables for kept items
  if (containerData.keptClassInfoTable)
    containerData.keptClassInfoTable.forEach(c => tables.classes.set(c.id, c));
  if (containerData.keptFieldInfoTable)
    containerData.keptFieldInfoTable.forEach(f => tables.fields.set(f.id, f));
  if (containerData.keptMethodInfoTable)
    containerData.keptMethodInfoTable.forEach(m => tables.methods.set(m.id, m));

  if (containerData.keepConstraintsTable)
    containerData.keepConstraintsTable.forEach(c => tables.keepConstraints.set(c.id, c));

  if (containerData.keepRuleBlastRadiusTable) {
    containerData.keepRuleBlastRadiusTable.forEach(r => {
      tables.rules.set(r.id, r);
      r.totalRadius = (r.blastRadius.classBlastRadius?.length || 0) +
        (r.blastRadius.fieldBlastRadius?.length || 0) +
        (r.blastRadius.methodBlastRadius?.length || 0);

      // Populate constraint stats
      const constraints = tables.keepConstraints.get(r.constraintsId);
      if (constraints && constraints.constraints) {
        constraints.constraints.forEach(c => {
          const stats = constraintStats[c];
          if (stats) {
            r.blastRadius.classBlastRadius?.forEach(id => stats.classes.add(id));
            r.blastRadius.methodBlastRadius?.forEach(id => stats.methods.add(id));
            r.blastRadius.fieldBlastRadius?.forEach(id => stats.fields.add(id));
          }
        });
      }

      // Group by file
      const fileName = getFileName(r.origin);
      if (!tables.files.has(fileName)) {
        tables.files.set(fileName, {
          name: fileName,
          origin: r.origin,
          rules: [],
          classIds: new Set(),
          methodIds: new Set(),
          fieldIds: new Set()
        });
      }
      const fileData = tables.files.get(fileName);
      fileData.rules.push(r);
      r.blastRadius.classBlastRadius?.forEach(id => fileData.classIds.add(id));
      r.blastRadius.methodBlastRadius?.forEach(id => fileData.methodIds.add(id));
      r.blastRadius.fieldBlastRadius?.forEach(id => fileData.fieldIds.add(id));
    });

    // Compute final counts for files
    tables.files.forEach(fileData => {
      fileData.classes = fileData.classIds.size;
      fileData.methods = fileData.methodIds.size;
      fileData.fields = fileData.fieldIds.size;
      fileData.totalRadius = fileData.classes + fileData.methods + fileData.fields;
    });
  }

  dropZone.classList.add('hidden');
  document.querySelector('.search-box').classList.remove('hidden');
  switchView('rules');
}

function renderStats() {
  const rules = containerData.keepRuleBlastRadiusTable || [];
  const unusedCount = rules.filter(r => r.totalRadius === 0).length;
  const redundantCount = rules.filter(r => r.blastRadius.subsumedBy && r.blastRadius.subsumedBy.length > 0).length;

  const format = (val) => (val || 0).toLocaleString();

  document.getElementById('stat-rules').textContent = format(rules.length);

  // Constraint-specific counts
  document.getElementById('stat-no-obfuscation-classes').textContent = format(constraintStats.DONT_OBFUSCATE.classes.size);
  document.getElementById('stat-no-obfuscation-methods').textContent = format(constraintStats.DONT_OBFUSCATE.methods.size);
  document.getElementById('stat-no-obfuscation-fields').textContent = format(constraintStats.DONT_OBFUSCATE.fields.size);

  document.getElementById('stat-no-optimization-classes').textContent = format(constraintStats.DONT_OPTIMIZE.classes.size);
  document.getElementById('stat-no-optimization-methods').textContent = format(constraintStats.DONT_OPTIMIZE.methods.size);
  document.getElementById('stat-no-optimization-fields').textContent = format(constraintStats.DONT_OPTIMIZE.fields.size);

  document.getElementById('stat-no-shrinking-classes').textContent = format(constraintStats.DONT_SHRINK.classes.size);
  document.getElementById('stat-no-shrinking-methods').textContent = format(constraintStats.DONT_SHRINK.methods.size);
  document.getElementById('stat-no-shrinking-fields').textContent = format(constraintStats.DONT_SHRINK.fields.size);

  if (containerData.buildInfo) {
    const info = containerData.buildInfo;
    // Reachable counts
    document.getElementById('stat-reachable-classes').textContent = format(info.liveClassCount);
    document.getElementById('stat-reachable-methods').textContent = format(info.liveMethodCount);
    document.getElementById('stat-reachable-fields').textContent = format(info.liveFieldCount);
    // Total counts
    document.getElementById('stat-total-classes').textContent = format(info.classCount);
    document.getElementById('stat-total-methods').textContent = format(info.methodCount);
    document.getElementById('stat-total-fields').textContent = format(info.fieldCount);
  } else {
    // Fallback if buildInfo is missing
    document.getElementById('stat-reachable-classes').textContent = "-";
    document.getElementById('stat-reachable-methods').textContent = "-";
    document.getElementById('stat-reachable-fields').textContent = "-";
    document.getElementById('stat-total-classes').textContent = "-";
    document.getElementById('stat-total-methods').textContent = "-";
    document.getElementById('stat-total-fields').textContent = "-";
  }

  const unusedBadge = document.getElementById('unused-count');
  unusedBadge.textContent = unusedCount;
  unusedBadge.classList.remove('hidden');

  const redundantBadge = document.getElementById('redundant-count');
  redundantBadge.textContent = redundantCount;
  redundantBadge.classList.remove('hidden');

  const rulesBadge = document.getElementById('rules-count');
  rulesBadge.textContent = rules.length;
  rulesBadge.classList.remove('hidden');

  const filesBadge = document.getElementById('files-count');
  filesBadge.textContent = tables.files.size;
  filesBadge.classList.remove('hidden');
}

function filterRules(query) {
  const rules = containerData.keepRuleBlastRadiusTable || [];
  const lowerQuery = query.toLowerCase();

  filteredRules = rules.filter(r =>
    r.source.toLowerCase().includes(lowerQuery)
  );

  filteredRules.sort((a, b) => b.totalRadius - a.totalRadius);
  renderRuleList();
}

function filterFiles(query) {
  const files = Array.from(tables.files.values());
  const lowerQuery = query.toLowerCase();

  filteredFiles = files.filter(f =>
    f.name.toLowerCase().includes(lowerQuery)
  );

  filteredFiles.sort((a, b) => b.totalRadius - a.totalRadius);
  renderFileList();
}

function renderRuleList() {
  ruleList.innerHTML = '';
  filteredRules.forEach(rule => {
    const isRedundant = rule.blastRadius.subsumedBy && rule.blastRadius.subsumedBy.length > 0;
    let sameOriginSubsumption = false;
    if (isRedundant) {
      const ruleOriginFile = getFileName(rule.origin);
      sameOriginSubsumption = rule.blastRadius.subsumedBy.some(id => {
        const subsumer = tables.rules.get(id);
        return subsumer && getFileName(subsumer.origin) === ruleOriginFile;
      });
    }

    const div = document.createElement('div');
    div.className = 'rule-item';
    div.dataset.ruleId = rule.id;
    const classes = rule.blastRadius.classBlastRadius?.length || 0;
    const methods = rule.blastRadius.methodBlastRadius?.length || 0;
    const fields = rule.blastRadius.fieldBlastRadius?.length || 0;
    div.innerHTML = `
            <div style="display: flex; align-items: flex-start; gap: 8px;">
                ${sameOriginSubsumption ? '<span title="Subsumed by rule in same file" style="color: #ff9800; cursor: help;">⚠️</span>' : ''}
                <div style="flex: 1;">
                    <span class="rule-name">${escapeHtml(rule.source)}</span>
                    ${currentView !== "unused" ? `
                        <div class="rule-stats">
                            Blast radius: ${rule.totalRadius}
                            ${formatStatsBreakdown(classes, methods, fields)}
                        </div>
                    ` : ''}
                </div>
            </div>
        `;
    div.onclick = () => selectRule(rule, div);
    ruleList.appendChild(div);
  });
}

function renderFileList() {
  ruleList.innerHTML = '';
  filteredFiles.forEach(file => {
    const div = document.createElement('div');
    div.className = 'rule-item';
    div.innerHTML = `
            <span class="rule-name">${escapeHtml(getOriginString(file.origin))}</span>
            <div class="rule-stats">
                Blast radius: ${file.totalRadius} across ${file.rules.length} rules
                <br>${formatStatsBreakdown(file.classes, file.methods, file.fields)}
            </div>
        `;
    div.onclick = () => selectFile(file, div);
    ruleList.appendChild(div);
  });
}

function selectRule(rule, element) {
  document.querySelectorAll('.rule-item').forEach(el => el.classList.remove('active'));
  element.classList.add('active');
  currentSelectedRule = rule;
  currentSubView = 'class';
  currentRulePagination = {
    classes: 100,
    methods: 100,
    fields: 100
  };
  renderRuleDetail(rule);
}

function selectFile(file, element) {
  document.querySelectorAll('.rule-item').forEach(el => el.classList.remove('active'));
  element.classList.add('active');
  renderFileDetail(file);
}

function renderRuleDetail(rule) {
  const isRedundant = rule.blastRadius.subsumedBy && rule.blastRadius.subsumedBy.length > 0;
  const ruleOriginFile = getFileName(rule.origin);

  let html = `
        <div class="card">
            <h2>Keep Rule Details</h2>
            ${isRedundant ? rule.blastRadius.subsumedBy.map(id => {
                const r = tables.rules.get(id);
                const isSameFile = r && getFileName(r.origin) === ruleOriginFile;
                const originStr = r ? getOriginString(r.origin) : "Unknown origin";
                return `
                    <div class="banner banner-warning">
                        <strong>Redundant Rule:</strong> This rule is fully subsumed by ${isSameFile ? 'another rule in the same file' : 'a rule in a different file'}:
                        <div style="margin-top: 0.5rem;">
                            <div class="subsumed-by-item" onclick="navigateToRule('${id}')">
                                <div style="font-weight: 500;">${r ? escapeHtml(r.source) : `Rule ID: ${id}`}</div>
                                <div style="font-size: 0.8rem; color: #666; margin-top: 2px;">Origin: ${escapeHtml(originStr)}</div>
                            </div>
                        </div>
                    </div>
                `;
            }).join('') : ''}
            <div class="keep-rule-source">${escapeHtml(rule.source)}</div>
            <p><strong>Origin:</strong> ${escapeHtml(getOriginString(rule.origin))}</p>
        </div>
        <div class="sub-tabs">
            <button class="sub-tab-button ${currentSubView === 'class' ? 'active' : ''}" onclick="switchSubView('class')">
                Matched Classes <span class="badge">${rule.blastRadius.classBlastRadius?.length || 0}</span>
            </button>
            <button class="sub-tab-button ${currentSubView === 'method' ? 'active' : ''}" onclick="switchSubView('method')">
                Matched Methods <span class="badge">${rule.blastRadius.methodBlastRadius?.length || 0}</span>
            </button>
            <button class="sub-tab-button ${currentSubView === 'field' ? 'active' : ''}" onclick="switchSubView('field')">
                Matched Fields <span class="badge">${rule.blastRadius.fieldBlastRadius?.length || 0}</span>
            </button>
        </div>
    `;

  html += renderBlastRadiusItems(rule.blastRadius);
  mainContent.innerHTML = html;
}

function renderFileDetail(file) {
  const usedRules = file.rules.filter(r => r.totalRadius > 0).sort((a, b) => b.totalRadius - a.totalRadius);
  const unusedRules = file.rules.filter(r => r.totalRadius === 0).sort((a, b) => a.source.localeCompare(b.source));

  let html = `
        <div class="card">
            <h2>File Details</h2>
            <p><strong>File:</strong> ${escapeHtml(file.name)}</p>
            <p><strong>Blast radius:</strong> ${file.totalRadius} items kept across ${file.rules.length} rules.</p>
        </div>
    `;

  if (usedRules.length > 0) {
    html += `
        <div class="card">
            <h2>Keep Rules in this File</h2>
            <div class="item-list">
                ${usedRules.map(r => `
                    <div class="rule-item" onclick="navigateToRule('${r.id}')">
                        <div class="rule-name">${escapeHtml(r.source)}</div>
                        <div class="rule-stats">Blast radius: ${r.totalRadius}</div>
                    </div>
                `).join('')}
            </div>
        </div>
    `;
  }

  if (unusedRules.length > 0) {
    html += `
        <div class="card">
            <h2>Unused Keep Rules in this File</h2>
            <div class="item-list">
                ${unusedRules.map(r => `
                    <div class="rule-item" onclick="navigateToRule('${r.id}')">
                        <div class="rule-name">${escapeHtml(r.source)}</div>
                    </div>
                `).join('')}
            </div>
        </div>
    `;
  }

  mainContent.innerHTML = html;
}

function renderBlastRadiusItems(blastRadius) {
  let html = "";

  const renderSection = (title, items, type, formatter, badgeClass, limit) => {
    if (currentSubView !== type) return "";
    if (!items || items.length === 0) {
      return `
        <div class="card">
            <p style="color: #666; font-style: italic;">No items matched this category.</p>
        </div>
      `;
    }
    const visibleItems = items.slice(0, limit);
    const hasMore = items.length > limit;

    return `
            <div class="card">
                <ul class="item-list">
                    ${visibleItems.map(id => {
                        const info = type === 'class' ? tables.classes.get(id) :
                                   (type === 'method' ? tables.methods.get(id) : tables.fields.get(id));
                        return info ? `<li>${escapeHtml(formatter(type === 'class' ? info.classReferenceId :
                                                     (type === 'method' ? info.methodReferenceId : info.fieldReferenceId)))}</li>`
                                    : `<li>Unknown ${type} ID ${id}</li>`;
                    }).join('')}
                </ul>
                ${hasMore ? `<button class="show-more-btn" onclick="showMore('${type}')">Show more (${items.length - limit} remaining)</button>` : ''}
            </div>
        `;
  };

  html += renderSection("Matched Classes", blastRadius.classBlastRadius, 'class', formatTypeName, 'badge-green', currentRulePagination.classes);
  html += renderSection("Matched Methods", blastRadius.methodBlastRadius, 'method', formatMethodName, 'badge-blue', currentRulePagination.methods);
  html += renderSection("Matched Fields", blastRadius.fieldBlastRadius, 'field', formatFieldName, 'badge-red', currentRulePagination.fields);

  return html;
}

function showMore(type) {
  if (type === 'class') currentRulePagination.classes += 100;
  else if (type === 'method') currentRulePagination.methods += 100;
  else if (type === 'field') currentRulePagination.fields += 100;

  if (currentSelectedRule) {
    renderRuleDetail(currentSelectedRule);
  }
}

function filterUnusedRules(query) {
  const rules = containerData.keepRuleBlastRadiusTable || [];
  const lowerQuery = query.toLowerCase();

  filteredRules = rules.filter(r =>
    r.totalRadius === 0 && r.source.toLowerCase().includes(lowerQuery)
  );

  // For unused rules, sort by source text
  filteredRules.sort((a, b) => a.source.localeCompare(b.source));
  renderRuleList();
}

function filterRedundantRules(query) {
  const rules = containerData.keepRuleBlastRadiusTable || [];
  const lowerQuery = query.toLowerCase();

  filteredRules = rules.filter(r =>
    r.blastRadius.subsumedBy && r.blastRadius.subsumedBy.length > 0 &&
    r.source.toLowerCase().includes(lowerQuery)
  );

  filteredRules.sort((a, b) => b.totalRadius - a.totalRadius);
  renderRuleList();
}

function navigateToRule(ruleId) {
  const rule = tables.rules.get(Number(ruleId));
  if (!rule) return;

  switchView('rules', true);
  const element = ruleList.querySelector(`[data-rule-id="${rule.id}"]`);
  if (element) {
    selectRule(rule, element);
    element.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }
}

function switchView(view, preserveDetail = false) {
  currentView = view;
  document.querySelectorAll('.tab-button').forEach(btn => {
    btn.classList.toggle('active', btn.getAttribute('onclick').includes(`'${view}'`));
  });

  searchInput.value = "";
  searchInput.placeholder = view === 'files' ? "Search files..." : "Search keep rules...";

  if (containerData) {
    if (view === 'rules') {
      filterRules("");
      if (!preserveDetail) {
        renderStatsOverview();
      }
    } else if (view === 'unused') {
      filterUnusedRules("");
      mainContent.innerHTML = '';
    } else if (view === 'redundant') {
      filterRedundantRules("");
      mainContent.innerHTML = '';
    } else if (view === 'files') {
      filterFiles("");
      mainContent.innerHTML = '';
    }
  } else {
    renderWelcomeMessage();
  }
}

function switchSubView(subView) {
  currentSubView = subView;
  if (currentSelectedRule) {
    renderRuleDetail(currentSelectedRule);
  }
}

function renderStatsOverview() {
  const template = document.getElementById('stats-overview-template');
  mainContent.innerHTML = '';
  mainContent.appendChild(template.content.cloneNode(true));
  renderStats();
}

function renderWelcomeMessage() {
  const template = document.getElementById('welcome-message-template');
  if (template) {
    mainContent.innerHTML = '';
    mainContent.appendChild(template.content.cloneNode(true));
  }
}

function renderLoading() {
  mainContent.innerHTML = `
    <div id="loading-screen">
      <div class="spinner"></div>
      <p>Processing data...</p>
    </div>
  `;
}

function formatTypeName(typeId) {
  const type = tables.types.get(typeId);
  if (!type) return "Unknown";
  return formatDescriptor(type.javaDescriptor);
}

function formatDescriptor(desc) {
  if (!desc) return "";
  let dimensions = 0;
  while (desc[dimensions] === '[') {
    dimensions++;
  }
  let base = desc.substring(dimensions);
  let res = "";
  if (base.startsWith('L') && base.endsWith(';')) {
    res = base.substring(1, base.length - 1).replace(/\//g, '.');
  } else if (base.length === 1) {
    switch (base[0]) {
      case 'V':
        res = "void";
        break;
      case 'Z':
        res = "boolean";
        break;
      case 'B':
        res = "byte";
        break;
      case 'S':
        res = "short";
        break;
      case 'C':
        res = "char";
        break;
      case 'I':
        res = "int";
        break;
      case 'J':
        res = "long";
        break;
      case 'F':
        res = "float";
        break;
      case 'D':
        res = "double";
        break;
      default:
        res = base;
    }
  } else {
    res = base;
  }
  for (let i = 0; i < dimensions; i++) {
    res += "[]";
  }
  return res;
}

function formatMethodName(methodRefId) {
  const ref = tables.methodRefs.get(methodRefId);
  if (!ref) return "Unknown method";
  const className = formatTypeName(ref.classReferenceId);
  const proto = tables.protos.get(ref.protoReferenceId);
  let params = "";
  if (proto && proto.parametersId) {
    const list = tables.typeLists.get(proto.parametersId);
    if (list && list.typeReferenceIds) {
      params = list.typeReferenceIds.map(id => formatTypeName(id)).join(', ');
    }
  }
  const returnType = proto ? formatTypeName(proto.returnTypeId) : "";
  return `${returnType} ${className}.${ref.name}(${params})`;
}

function formatFieldName(fieldRefId) {
  const ref = tables.fieldRefs.get(fieldRefId);
  if (!ref) return "Unknown field";
  const className = formatTypeName(ref.classReferenceId);
  const type = formatTypeName(ref.typeReferenceId);
  return `${type} ${className}.${ref.name}`;
}

function getFileName(origin) {
  if (!origin) return "Unknown";
  const fileOrigin = tables.origins.get(origin.fileOriginId);
  return fileOrigin ? fileOrigin.filename : "Unknown";
}

function getMavenCoordinate(origin) {
  if (!origin) return null;
  const fileOrigin = tables.origins.get(origin.fileOriginId);
  return fileOrigin?.mavenCoordinate;
}

function getMavenCoordinateString(mavenCoordinate) {
  return mavenCoordinate.groupId + ":" + mavenCoordinate.artifactId + ":" + mavenCoordinate.version;
}

function getOriginString(origin) {
  if (!origin) return "Unknown";
  const mavenCoordinate = getMavenCoordinate(origin);
  if (mavenCoordinate) {
    return getMavenCoordinateString(mavenCoordinate);
  }
  const name = getFileName(origin);
  let res = name;
  if (origin.lineNumber) {
    res += ":" + origin.lineNumber;
    if (origin.columnNumber) res += ":" + origin.columnNumber;
  }
  return res;
}

function escapeHtml(text) {
  if (!text) return "";
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

function formatStatsBreakdown(classes, methods, fields) {
  const parts = [];
  if (classes > 0) parts.push(`${classes} class${classes !== 1 ? 'es' : ''}`);
  if (methods > 0) parts.push(`${methods} method${methods !== 1 ? 's' : ''}`);
  if (fields > 0) parts.push(`${fields} field${fields !== 1 ? 's' : ''}`);
  return parts.length > 0 ? `(${parts.join(', ')})` : '';
}

if (!containerData && !embeddedProtoDataSource) {
  renderWelcomeMessage();
  dropZone.classList.remove('hidden');
}
