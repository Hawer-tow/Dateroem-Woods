// Tiny drawing kit for the hand-laid documentation diagrams: bands, boxes,
// cylinders and routed arrows on one SVG, in the documentation palette.
(function () {
  const NS = 'http://www.w3.org/2000/svg';
  const STY = {
    client: { fill: '#F6EFE6', stroke: '#8A5A2B', text: '#1E2622' },
    edge: { fill: '#FFF7E8', stroke: '#C9A46A', text: '#1E2622' },
    bff: { fill: '#EAF3EE', stroke: '#2E6B4F', text: '#1E2622' },
    core: { fill: '#1F4D3A', stroke: '#1F4D3A', text: '#FFFFFF' },
    data: { fill: '#FFFFFF', stroke: '#2E6B4F', text: '#1E2622' },
    ext: { fill: '#F4F5F3', stroke: '#8C9590', text: '#1E2622', dash: '5 4' },
    plain: { fill: '#FFFFFF', stroke: '#B9C4BD', text: '#1E2622' },
  };
  let svg;
  function el(name, attrs, parent) {
    const e = document.createElementNS(NS, name);
    for (const k in attrs) e.setAttribute(k, attrs[k]);
    (parent || svg).appendChild(e);
    return e;
  }
  function text(x, y, str, opts) {
    const o = Object.assign({ size: 15, weight: 400, color: '#1E2622', anchor: 'middle' }, opts || {});
    const t = el('text', { x, y, 'font-size': o.size, 'font-weight': o.weight, fill: o.color, 'text-anchor': o.anchor, 'font-family': "'Segoe UI', Calibri, Arial, sans-serif" });
    t.textContent = str;
    if (o.halo) {
      t.setAttribute('paint-order', 'stroke');
      t.setAttribute('stroke', '#FFFFFF');
      t.setAttribute('stroke-width', '5');
      t.setAttribute('stroke-linejoin', 'round');
    }
    // Shrink to fit a box's inner width rather than spill over its edge.
    if (o.fit) {
      const len = t.getComputedTextLength();
      if (len > o.fit) t.setAttribute('font-size', (o.size * o.fit) / len);
    }
    return t;
  }
  window.Draw = {
    start(w, h) {
      svg = el('svg', { xmlns: NS, width: w, height: h, viewBox: `0 0 ${w} ${h}` }, document.getElementById('wrap'));
      const defs = el('defs', {});
      const m = el('marker', { id: 'arr', viewBox: '0 0 10 10', refX: 9, refY: 5, markerWidth: 7, markerHeight: 7, orient: 'auto-start-reverse' }, defs);
      el('path', { d: 'M0,0 L10,5 L0,10 z', fill: '#4A5550' }, m);
      el('rect', { x: 0, y: 0, width: w, height: h, fill: '#FFFFFF' });
    },
    // where: 'tl' (top-left, default), 'tr' (top-right), 'bl' (bottom-left)
    band(x, y, w, h, label, where) {
      el('rect', { x, y, width: w, height: h, rx: 12, fill: '#F7F8F6', stroke: '#C9D2CC' });
      const pos = where || 'tl';
      const right = pos === 'tr' || pos === 'br';
      const tx = right ? x + w - 14 : x + 14;
      const ty = pos === 'bl' || pos === 'br' ? y + h - 12 : y + 22;
      text(tx, ty, label.toUpperCase(), { size: 12.5, weight: 600, color: '#5F6B66', anchor: right ? 'end' : 'start' });
    },
    box(x, y, w, h, title, sub, kind) {
      const s = STY[kind || 'plain'];
      el('rect', { x, y, width: w, height: h, rx: 8, fill: s.fill, stroke: s.stroke, 'stroke-width': 1.6, 'stroke-dasharray': s.dash || 'none' });
      const lines = sub ? sub.split('\n') : [];
      const total = 20 + lines.length * 17;
      let cy = y + h / 2 - total / 2 + 15;
      text(x + w / 2, cy, title, { size: 16, weight: 600, color: s.text, fit: w - 14 });
      lines.forEach((ln) => { cy += 18; text(x + w / 2, cy, ln, { size: 13.5, color: s.text, fit: w - 14 }); });
    },
    cylinder(x, y, w, h, title, sub) {
      const ry = 11;
      el('path', { d: `M${x},${y + ry} v${h - 2 * ry} a${w / 2},${ry} 0 0 0 ${w},0 v${-(h - 2 * ry)}`, fill: '#FFFFFF', stroke: '#2E6B4F', 'stroke-width': 1.6 });
      el('ellipse', { cx: x + w / 2, cy: y + ry, rx: w / 2, ry, fill: '#FFFFFF', stroke: '#2E6B4F', 'stroke-width': 1.6 });
      const lines = sub ? sub.split('\n') : [];
      let cy = y + ry + (h - ry) / 2 - (lines.length * 17) / 2 + 4;
      text(x + w / 2, cy, title, { size: 16, weight: 600 });
      lines.forEach((ln) => { cy += 18; text(x + w / 2, cy, ln, { size: 13.5 }); });
    },
    // points: [[x,y], ...]; label at [lx, ly]
    arrow(points, label, at, opts) {
      const o = Object.assign({ dashed: false, both: false, anchor: 'middle' }, opts || {});
      const d = points.map((p, i) => `${i ? 'L' : 'M'}${p[0]},${p[1]}`).join(' ');
      const attrs = { d, fill: 'none', stroke: '#4A5550', 'stroke-width': 1.6, 'marker-end': 'url(#arr)' };
      if (o.both) attrs['marker-start'] = 'url(#arr)';
      if (o.dashed) attrs['stroke-dasharray'] = '6 5';
      el('path', attrs);
      if (label) text(at[0], at[1], label, { size: 13, color: '#2B3330', anchor: o.anchor, halo: true });
    },
    note(x, y, str, opts) { text(x, y, str, Object.assign({ size: 13, color: '#5F6B66' }, opts || {})); },
    done() { window.__state = 'ready'; },
  };
})();
