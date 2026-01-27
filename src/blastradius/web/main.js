// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
let protobufRoot = null;
let containerData = null;
let filteredRules = [];
let filteredFiles = [];
let currentView = 'rules'; // 'rules' or 'files'
let currentSelectedRule = null;
let currentRulePagination = { classes: 100, methods: 100, fields: 100 };

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
    files: new Map(), // filename -> { rules: [], totalRadius: 0, classes: 0, methods: 0, fields: 0 }
    methodRefs: new Map(),
    fieldRefs: new Map()
};

const dropZone = document.getElementById('drop-zone');
const fileInput = document.getElementById('file-input');
const ruleList = document.getElementById('rule-list');
const searchInput = document.getElementById('search-input');
const mainContent = document.getElementById('main-content');
const statsOverview = document.getElementById('stats-overview');

// Load Protobuf definition
protobuf.load("blastradius.proto", function(err, root) {
    if (err) {
        console.error("Error loading proto:", err);
        alert("Failed to load blastradius.proto.");
        return;
    }
    protobufRoot = root;
});

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

searchInput.addEventListener('input', (e) => {
    if (currentView === 'rules') {
        filterRules(e.target.value);
    } else if (currentView === 'unused') {
        filterUnusedRules(e.target.value);
    } else if (currentView === 'redundant') {
        filterRedundantRules(e.target.value);
    } else {
        filterFiles(e.target.value);
    }
});

function handleFile(file) {
    if (!protobufRoot) {
        alert("Protobuf definition not loaded yet.");
        return;
    }

    const reader = new FileReader();
    reader.onload = function(e) {
        const arrayBuffer = e.target.result;
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
        }
    };
    reader.readAsArrayBuffer(file);
}

function processData() {
    // Clear previous data
    Object.values(tables).forEach(map => map instanceof Map ? map.clear() : null);
    tables.files = new Map();

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

    if (containerData.keepRuleBlastRadiusTable) {
        containerData.keepRuleBlastRadiusTable.forEach(r => {
            tables.rules.set(r.id, r);
            r.totalRadius = (r.blastRadius.classBlastRadius?.length || 0) +
                           (r.blastRadius.fieldBlastRadius?.length || 0) +
                           (r.blastRadius.methodBlastRadius?.length || 0);

            // Group by file
            const fileName = getFileName(r.origin);
            if (!tables.files.has(fileName)) {
                tables.files.set(fileName, {
                    name: fileName,
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

    renderStats();
    if (currentView === 'rules') {
        filterRules("");
    } else if (currentView === 'unused') {
        filterUnusedRules("");
    } else if (currentView === 'redundant') {
        filterRedundantRules("");
    } else {
        filterFiles("");
    }

    dropZone.classList.add('hidden');
    statsOverview.classList.remove('hidden');
    document.querySelector('.search-box').classList.remove('hidden');
    document.getElementById('welcome-message').classList.add('hidden');
}

function renderStats() {
    const rules = containerData.keepRuleBlastRadiusTable || [];
    const unusedCount = rules.filter(r => r.totalRadius === 0).length;
    const redundantCount = rules.filter(r => r.blastRadius.subsumedBy && r.blastRadius.subsumedBy.length > 0).length;

    document.getElementById('stat-rules').textContent = rules.length;
    document.getElementById('stat-classes').textContent = containerData.keptClassInfoTable?.length || 0;
    document.getElementById('stat-methods').textContent = containerData.keptMethodInfoTable?.length || 0;
    document.getElementById('stat-fields').textContent = containerData.keptFieldInfoTable?.length || 0;

    const unusedBadge = document.getElementById('unused-count');
    unusedBadge.textContent = unusedCount;
    unusedBadge.classList.remove('hidden');

    const redundantBadge = document.getElementById('redundant-count');
    redundantBadge.textContent = redundantCount;
    redundantBadge.classList.remove('hidden');
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
        const div = document.createElement('div');
        div.className = 'rule-item';
        div.innerHTML = `
            <span class="rule-name">${escapeHtml(rule.source)}</span>
            <div class="rule-stats">
                Blast radius: ${rule.totalRadius}
                (${rule.blastRadius.classBlastRadius?.length || 0} classes,
                 ${rule.blastRadius.methodBlastRadius?.length || 0} methods,
                 ${rule.blastRadius.fieldBlastRadius?.length || 0} fields)
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
            <span class="rule-name">${escapeHtml(file.name)}</span>
            <div class="rule-stats">
                Total radius: ${file.totalRadius} across ${file.rules.length} rules
                <br>(${file.classes} classes, ${file.methods} methods, ${file.fields} fields)
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
    currentRulePagination = { classes: 100, methods: 100, fields: 100 };
    renderRuleDetail(rule);
}

function selectFile(file, element) {
    document.querySelectorAll('.rule-item').forEach(el => el.classList.remove('active'));
    element.classList.add('active');
    renderFileDetail(file);
}

function renderRuleDetail(rule) {
    const isRedundant = rule.blastRadius.subsumedBy && rule.blastRadius.subsumedBy.length > 0;
    let html = `
        <div class="card">
            <h2>Keep Rule Details</h2>
            ${isRedundant ? rule.blastRadius.subsumedBy.map(id => {
                const r = tables.rules.get(id);
                const originStr = r ? getOriginString(r.origin) : "Unknown origin";
                return `
                    <div class="banner banner-warning">
                        <strong>Redundant Rule:</strong> This rule is fully subsumed by:
                        <div style="margin-top: 0.5rem;">
                            <div class="subsumed-by-item">
                                <div style="font-weight: 500;">${r ? escapeHtml(r.source) : `Rule ID: ${id}`}</div>
                                <div style="font-size: 0.8rem; color: #666; margin-top: 2px;">Origin: ${originStr}</div>
                            </div>
                        </div>
                    </div>
                `;
            }).join('') : ''}
            <div class="keep-rule-source">${escapeHtml(rule.source)}</div>
            <p><strong>Origin:</strong> ${getOriginString(rule.origin)}</p>
        </div>
    `;

    html += renderBlastRadiusItems(rule.blastRadius);
    mainContent.innerHTML = html;
}

function renderFileDetail(file) {
    let html = `
        <div class="card">
            <h2>File Details</h2>
            <p><strong>File:</strong> ${escapeHtml(file.name)}</p>
            <p><strong>Total Impact:</strong> ${file.totalRadius} items kept across ${file.rules.length} rules.</p>
        </div>
        <div class="card">
            <h2>Keep Rules in this File</h2>
            <div class="item-list">
                ${file.rules.sort((a,b) => b.totalRadius - a.totalRadius).map(r => `
                    <div style="padding: 1rem; border-bottom: 1px solid #eee;">
                        <div class="rule-name">${escapeHtml(r.source)}</div>
                        <div class="rule-stats">Blast radius: ${r.totalRadius}</div>
                    </div>
                `).join('')}
            </div>
        </div>
    `;
    mainContent.innerHTML = html;
}

function renderBlastRadiusItems(blastRadius) {
    let html = "";

    const renderSection = (title, items, type, formatter, badgeClass, limit) => {
        if (!items || items.length === 0) return "";
        const visibleItems = items.slice(0, limit);
        const hasMore = items.length > limit;

        return `
            <div class="card">
                <div class="section-title">${title} <span class="badge ${badgeClass}">${items.length}</span></div>
                <ul class="item-list">
                    ${visibleItems.map(id => {
                        const info = type === 'class' ? tables.classes.get(id) :
                                   (type === 'method' ? tables.methods.get(id) : tables.fields.get(id));
                        return info ? `<li>${formatter(type === 'class' ? info.classReferenceId :
                                                     (type === 'method' ? info.methodReferenceId : info.fieldReferenceId))}</li>`
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

function switchView(view) {
    currentView = view;
    document.querySelectorAll('.tab-button').forEach(btn => {
        btn.classList.toggle('active', btn.getAttribute('onclick').includes(view));
    });

    searchInput.value = "";
    searchInput.placeholder = view === 'files' ? "Search files..." : "Search keep rules...";

    if (containerData) {
        if (view === 'rules') {
            filterRules("");
        } else if (view === 'unused') {
            filterUnusedRules("");
        } else if (view === 'redundant') {
            filterRedundantRules("");
        } else {
            filterFiles("");
        }
    }
    mainContent.innerHTML = '';
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
            case 'V': res = "void"; break;
            case 'Z': res = "boolean"; break;
            case 'B': res = "byte"; break;
            case 'S': res = "short"; break;
            case 'C': res = "char"; break;
            case 'I': res = "int"; break;
            case 'J': res = "long"; break;
            case 'F': res = "float"; break;
            case 'D': res = "double"; break;
            default: res = base;
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

function getOriginString(origin) {
    if (!origin) return "Unknown";
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