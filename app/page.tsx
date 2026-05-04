"use client";
import React, { useState, Suspense, useRef, useMemo } from 'react';
import { Canvas } from '@react-three/fiber';
import { OrbitControls, PerspectiveCamera, Environment } from '@react-three/drei';
import { Box, CheckSquare, Square, Wind, ArrowUpDown, Layers, MousePointer2, RotateCcw, Activity, Save, Upload, RefreshCw } from 'lucide-react';
import * as THREE from 'three';
// @ts-ignore
import { STLExporter } from 'three/examples/jsm/exporters/STLExporter';

// --- PRESETS ---
const VASE_PRESETS: Record<string, any> = {
  'Default': { vH:180, vRB:55, vRM:70, vRT:45, vMP:0.5,  vTwistG:0.0, vTwistB:0,   vTwistT:1.5, vUseTB:true,  vUseTT:true,  vSeg:40, vType:'square', vWall:2.4, vBottom:5, vPattern:0.05 },
  'Classic': { vH:200, vRB:40, vRM:52, vRT:35, vMP:0.38, vTwistG:0,   vTwistB:0,   vTwistT:0,   vUseTB:false, vUseTT:false, vSeg:6,  vType:'circle', vWall:2.5, vBottom:6, vPattern:0    },
  'Twisted': { vH:220, vRB:45, vRM:62, vRT:48, vMP:0.5,  vTwistG:7,   vTwistB:1.2, vTwistT:2.1, vUseTB:true,  vUseTT:true,  vSeg:8,  vType:'wave',   vWall:2.2, vBottom:5, vPattern:0.1  },
  'Star':    { vH:160, vRB:60, vRM:82, vRT:55, vMP:0.55, vTwistG:2,   vTwistB:0,   vTwistT:0,   vUseTB:false, vUseTT:false, vSeg:5,  vType:'star',   vWall:2.8, vBottom:6, vPattern:0.22 },
  'Wide':    { vH:110, vRB:95, vRM:115,vRT:68, vMP:0.62, vTwistG:0,   vTwistB:0,   vTwistT:0,   vUseTB:false, vUseTT:false, vSeg:6,  vType:'square', vWall:3.0, vBottom:7, vPattern:0.06 },
};

const ORG_PRESETS: Record<string, any> = {
  'Default': { cols:4, rows:2, cellW:50, cellL:55, depth:105, wall:2.0, backWall:3.0, radius:8,  hasBottom:true },
  'Desk':    { cols:6, rows:2, cellW:38, cellL:58, depth:75,  wall:1.8, backWall:2.5, radius:5,  hasBottom:true },
  'Deep':    { cols:3, rows:1, cellW:72, cellL:82, depth:155, wall:2.5, backWall:4.0, radius:10, hasBottom:true },
  'Tiny':    { cols:8, rows:3, cellW:24, cellL:28, depth:55,  wall:1.5, backWall:2.0, radius:3,  hasBottom:true },
};

// --- MODELS ---
function OrganizerModel({ cols, rows, cellW, cellL, depth, wall, backWall, radius, color, hasBottom, wireframe }: any) {
  const totalW = cols * cellW + (cols + 1) * wall;
  const totalL = rows * cellL + (rows + 1) * wall;

  const outerShape = useMemo(() => {
    const x = -totalW / 2, y = -totalL / 2, r = Math.min(radius, totalW / 2 - 0.1, totalL / 2 - 0.1);
    const s = new THREE.Shape();
    s.moveTo(x + r, y); s.lineTo(x + totalW - r, y); s.absarc(x + totalW - r, y + r, r, -Math.PI / 2, 0, false);
    s.lineTo(x + totalW, y + totalL - r); s.absarc(x + totalW - r, y + totalL - r, r, 0, Math.PI / 2, false);
    s.lineTo(x + r, y + totalL); s.absarc(x + r, y + totalL - r, r, Math.PI / 2, Math.PI, false);
    s.lineTo(x, y + r); s.absarc(x + r, y + r, r, Math.PI, -Math.PI / 2, false);
    const hole = new THREE.Path();
    const ix = x + wall, iy = y + wall, iw = totalW - 2 * wall, il = totalL - 2 * wall, ir = Math.max(0, r - wall);
    hole.moveTo(ix + ir, iy); hole.lineTo(ix + iw - ir, iy); hole.absarc(ix + iw - ir, iy + ir, ir, -Math.PI / 2, 0, false);
    hole.lineTo(ix + iw, iy + il - ir); hole.absarc(ix + iw - ir, iy + il - ir, ir, 0, Math.PI / 2, false);
    hole.lineTo(ix + ir, iy + il); hole.absarc(ix + ir, iy + il - ir, ir, Math.PI / 2, Math.PI, false);
    hole.lineTo(ix, iy + ir); hole.absarc(ix + ir, iy + ir, ir, Math.PI, -Math.PI / 2, false);
    s.holes.push(hole); return s;
  }, [totalW, totalL, radius, wall]);

  const bottomShape = useMemo(() => {
    const x = -totalW / 2, y = -totalL / 2, r = Math.min(radius, totalW / 2 - 0.1, totalL / 2 - 0.1);
    const s = new THREE.Shape();
    s.moveTo(x + r, y); s.lineTo(x + totalW - r, y); s.absarc(x + totalW - r, y + r, r, -Math.PI / 2, 0, false);
    s.lineTo(x + totalW, y + totalL - r); s.absarc(x + totalW - r, y + totalL - r, r, 0, Math.PI / 2, false);
    s.lineTo(x + r, y + totalL); s.absarc(x + r, y + totalL - r, r, Math.PI / 2, Math.PI, false);
    s.lineTo(x, y + r); s.absarc(x + r, y + r, r, Math.PI, -Math.PI / 2, false);
    return s;
  }, [totalW, totalL, radius]);

  return (
    <group rotation={[-Math.PI / 2, 0, 0]}>
      <mesh castShadow receiveShadow>
        <extrudeGeometry args={[outerShape, { depth, bevelEnabled: false }]} />
        <meshStandardMaterial color={color} roughness={0.4} wireframe={wireframe} />
      </mesh>
      {hasBottom && (
        <mesh position={[0, 0, -backWall]} castShadow receiveShadow>
          <extrudeGeometry args={[bottomShape, { depth: backWall, bevelEnabled: false }]} />
          <meshStandardMaterial color={color} roughness={0.4} wireframe={wireframe} />
        </mesh>
      )}
      <group position={[(-totalW / 2 + wall), (-totalL / 2 + wall), depth / 2]}>
        {Array.from({ length: cols - 1 }).map((_, c) => (
          <mesh key={`v-${c}`} position={[(c + 1) * (cellW + wall) - wall / 2, (totalL - 2 * wall) / 2, 0]}>
            <boxGeometry args={[wall, totalL - 2 * wall, depth]} />
            <meshStandardMaterial color={color} wireframe={wireframe} />
          </mesh>
        ))}
        {Array.from({ length: rows - 1 }).map((_, r) => (
          <mesh key={`h-${r}`} position={[(totalW - 2 * wall) / 2, (r + 1) * (cellL + wall) - wall / 2, 0]}>
            <boxGeometry args={[totalW - 2 * wall, wall, depth]} />
            <meshStandardMaterial color={color} wireframe={wireframe} />
          </mesh>
        ))}
      </group>
    </group>
  );
}

function VaseModel({ height, rBase, rMid, rTop, midPos, twistGlobal, twistBase, twistTop, useTwistB, useTwistT, segments, shapeType, color, vWall, vBottom, patternStr, wireframe }: any) {
  const geometry = useMemo(() => {
    const points = [];
    const segmentsProf = 140;
    for (let i = 0; i <= segmentsProf; i++) {
      const t = i / segmentsProf;
      const smoothT = THREE.MathUtils.smoothstep(t, Math.max(0, midPos - 0.2), Math.min(1, midPos + 0.2));
      const rLower = THREE.MathUtils.lerp(rBase, rMid, Math.sin((t / midPos) * Math.PI / 2));
      const rUpper = THREE.MathUtils.lerp(rMid, rTop, Math.sin(((t - midPos) / (1 - midPos)) * Math.PI / 2));
      const r = THREE.MathUtils.lerp(rLower, rUpper, smoothT);
      points.push(new THREE.Vector2(r, t * height));
    }
    for (let i = segmentsProf; i >= 0; i--) {
      const t = i / segmentsProf;
      const yPos = t * height;
      if (yPos >= vBottom) {
        const smoothT = THREE.MathUtils.smoothstep(t, Math.max(0, midPos - 0.2), Math.min(1, midPos + 0.2));
        const rLower = THREE.MathUtils.lerp(rBase, rMid, Math.sin((t / midPos) * Math.PI / 2));
        const rUpper = THREE.MathUtils.lerp(rMid, rTop, Math.sin(((t - midPos) / (1 - midPos)) * Math.PI / 2));
        const r = THREE.MathUtils.lerp(rLower, rUpper, smoothT);
        points.push(new THREE.Vector2(Math.max(0.1, r - vWall), yPos));
      }
    }
    points.push(new THREE.Vector2(0, vBottom), new THREE.Vector2(0, 0));

    const lathe = new THREE.LatheGeometry(points, 360);
    const pos = lathe.attributes.position;
    const activeTwistB = useTwistB ? twistBase : 0;
    const activeTwistT = useTwistT ? twistTop : 0;

    for (let i = 0; i < pos.count; i++) {
      const x = pos.getX(i), y = pos.getY(i), z = pos.getZ(i);
      const dist = Math.sqrt(x * x + z * z);
      const angle = Math.atan2(z, x);
      const tY = y / height;
      if (dist > 1.5) {
        let mod = 1;
        const pattern = Math.sin(angle * segments) * Math.cos(tY * Math.PI * 10);
        if (shapeType === 'star') mod = 1 + Math.abs(Math.cos(angle * (segments / 2))) * (0.2 + pattern * patternStr);
        else if (shapeType === 'wave') mod = 1 + Math.sin(angle * segments) * (0.12 + pattern * patternStr);
        else if (shapeType === 'square') mod = 1 + (Math.tanh(Math.sin(angle * segments) / 0.15) * (0.1 + pattern * patternStr));
        else if (shapeType === 'circle') mod = 1 + (pattern * patternStr);
        let currentTwist = tY * twistGlobal;
        if (tY <= midPos) currentTwist += (tY / midPos) * activeTwistB;
        else currentTwist += activeTwistB + ((tY - midPos) / (1 - midPos)) * activeTwistT;
        pos.setXYZ(i, Math.cos(angle + currentTwist) * dist * mod, y, Math.sin(angle + currentTwist) * dist * mod);
      }
    }
    lathe.computeVertexNormals();
    return lathe;
  }, [height, rBase, rMid, rTop, midPos, twistGlobal, twistBase, twistTop, useTwistB, useTwistT, segments, shapeType, vWall, vBottom, patternStr]);

  return (
    <mesh geometry={geometry} castShadow receiveShadow>
      <meshStandardMaterial color={color} side={THREE.DoubleSide} roughness={0.4} wireframe={wireframe} />
    </mesh>
  );
}

// --- MAIN PAGE ---
export default function Home() {
  const groupRef = useRef<THREE.Group>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [tab, setTab] = useState('vases');
  const [mainColor, setMainColor] = useState('#1e3a8a');
  const [wireframe, setWireframe] = useState(false);
  const [showGrid, setShowGrid] = useState(true);

  // Organizer States
  const [cols, setCols] = useState(4);
  const [rows, setRows] = useState(2);
  const [cellW, setCellW] = useState(50);
  const [cellL, setCellL] = useState(55);
  const [depth, setDepth] = useState(105);
  const [wall, setWall] = useState(2.0);
  const [backWall, setBackWall] = useState(3.0);
  const [radius, setRadius] = useState(8);
  const [hasBottom, setHasBottom] = useState(true);

  // Vase States
  const [vH, setVH] = useState(180);
  const [vRB, setVRB] = useState(55);
  const [vRM, setVRM] = useState(70);
  const [vRT, setVRT] = useState(45);
  const [vMP, setVMP] = useState(0.5);
  const [vTwistG, setVTwistG] = useState(0.0);
  const [vTwistB, setVTwistB] = useState(0);
  const [vTwistT, setVTwistT] = useState(1.5);
  const [vUseTB, setVUseTB] = useState(true);
  const [vUseTT, setVUseTT] = useState(true);
  const [vSeg, setVSeg] = useState(40);
  const [vType, setVType] = useState('square');
  const [vWall, setVWall] = useState(2.4);
  const [vBottom, setVBottom] = useState(5);
  const [vPattern, setVPattern] = useState(0.05);

  // --- DIMENSIONS & WEIGHT ---
  const orgDims = useMemo(() => {
    const w = Math.round(cols * cellW + (cols + 1) * wall);
    const l = Math.round(rows * cellL + (rows + 1) * wall);
    return { w, l, d: depth };
  }, [cols, rows, cellW, cellL, depth, wall]);

  const orgWeight = useMemo(() => {
    const tW = cols * cellW + (cols + 1) * wall;
    const tL = rows * cellL + (rows + 1) * wall;
    const outerArea = tW * tL;
    const innerArea = (tW - 2 * wall) * (tL - 2 * wall);
    const shellVol = (outerArea - innerArea) * depth;
    const bottomVol = hasBottom ? outerArea * backWall : 0;
    const vDivVol = (cols - 1) * wall * (tL - 2 * wall) * depth;
    const hDivVol = (rows - 1) * wall * (tW - 2 * wall) * depth;
    return ((shellVol + bottomVol + vDivVol + hDivVol) / 1000 * 1.24).toFixed(0);
  }, [cols, rows, cellW, cellL, depth, wall, backWall, hasBottom]);

  const vaseDims = useMemo(() => {
    const maxR = Math.max(vRB, vRM, vRT);
    return { w: Math.round(maxR * 2), l: Math.round(maxR * 2), d: vH };
  }, [vRB, vRM, vRT, vH]);

  const vaseWeight = useMemo(() => {
    const avgR = (vRB + vRM + vRT) / 3;
    const shellVol = Math.PI * (2 * avgR * vWall - vWall * vWall) * vH;
    const bottomVol = Math.PI * (vRB / 10) * (vRB / 10) * (vBottom / 10) * 1000;
    return ((shellVol + bottomVol) / 1000 * 1.24).toFixed(0);
  }, [vRB, vRM, vRT, vH, vWall, vBottom]);

  const dims = tab === 'organizer' ? orgDims : vaseDims;
  const weight = tab === 'organizer' ? orgWeight : vaseWeight;

  // --- PRESET APPLY ---
  const applyVasePreset = (name: string) => {
    const p = VASE_PRESETS[name]; if (!p) return;
    setVH(p.vH); setVRB(p.vRB); setVRM(p.vRM); setVRT(p.vRT); setVMP(p.vMP);
    setVTwistG(p.vTwistG); setVTwistB(p.vTwistB); setVTwistT(p.vTwistT);
    setVUseTB(p.vUseTB); setVUseTT(p.vUseTT); setVSeg(p.vSeg); setVType(p.vType);
    setVWall(p.vWall); setVBottom(p.vBottom); setVPattern(p.vPattern);
  };

  const applyOrgPreset = (name: string) => {
    const p = ORG_PRESETS[name]; if (!p) return;
    setCols(p.cols); setRows(p.rows); setCellW(p.cellW); setCellL(p.cellL);
    setDepth(p.depth); setWall(p.wall); setBackWall(p.backWall);
    setRadius(p.radius); setHasBottom(p.hasBottom);
  };

  // --- SAVE / LOAD ---
  const saveParams = () => {
    const data = tab === 'vases'
      ? { type: 'vase', vH, vRB, vRM, vRT, vMP, vTwistG, vTwistB, vTwistT, vUseTB, vUseTT, vSeg, vType, vWall, vBottom, vPattern, mainColor }
      : { type: 'organizer', cols, rows, cellW, cellL, depth, wall, backWall, radius, hasBottom, mainColor };
    const link = document.createElement('a');
    link.href = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }));
    link.download = `dryguny_${tab}_params.json`;
    link.click();
  };

  const loadParams = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]; if (!file) return;
    const reader = new FileReader();
    reader.onload = (ev) => {
      try {
        const d = JSON.parse(ev.target?.result as string);
        if (d.mainColor) setMainColor(d.mainColor);
        if (d.type === 'vase') {
          setTab('vases');
          if (d.vH !== undefined) setVH(d.vH); if (d.vRB !== undefined) setVRB(d.vRB);
          if (d.vRM !== undefined) setVRM(d.vRM); if (d.vRT !== undefined) setVRT(d.vRT);
          if (d.vMP !== undefined) setVMP(d.vMP); if (d.vTwistG !== undefined) setVTwistG(d.vTwistG);
          if (d.vTwistB !== undefined) setVTwistB(d.vTwistB); if (d.vTwistT !== undefined) setVTwistT(d.vTwistT);
          if (d.vUseTB !== undefined) setVUseTB(d.vUseTB); if (d.vUseTT !== undefined) setVUseTT(d.vUseTT);
          if (d.vSeg !== undefined) setVSeg(d.vSeg); if (d.vType !== undefined) setVType(d.vType);
          if (d.vWall !== undefined) setVWall(d.vWall); if (d.vBottom !== undefined) setVBottom(d.vBottom);
          if (d.vPattern !== undefined) setVPattern(d.vPattern);
        } else if (d.type === 'organizer') {
          setTab('organizer');
          if (d.cols !== undefined) setCols(d.cols); if (d.rows !== undefined) setRows(d.rows);
          if (d.cellW !== undefined) setCellW(d.cellW); if (d.cellL !== undefined) setCellL(d.cellL);
          if (d.depth !== undefined) setDepth(d.depth); if (d.wall !== undefined) setWall(d.wall);
          if (d.backWall !== undefined) setBackWall(d.backWall); if (d.radius !== undefined) setRadius(d.radius);
          if (d.hasBottom !== undefined) setHasBottom(d.hasBottom);
        }
      } catch { alert('Invalid JSON file'); }
    };
    reader.readAsText(file);
    e.target.value = '';
  };

  // --- EXPORT STL ---
  const exportSTL = () => {
    if (!groupRef.current) return;
    const exporter = new STLExporter();
    const result = exporter.parse(groupRef.current, { binary: true });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(new Blob([result], { type: 'application/octet-stream' }));
    link.download = `Dryguny_${tab}.stl`;
    link.click();
  };

  const activePresets = tab === 'vases' ? VASE_PRESETS : ORG_PRESETS;
  const applyPreset = tab === 'vases' ? applyVasePreset : applyOrgPreset;

  return (
    <main className="h-screen w-full bg-[#0a0a0a] flex flex-col p-6 overflow-hidden text-white font-sans uppercase tracking-tight selection:bg-white selection:text-black">

      {/* Hidden file input for JSON load */}
      <input ref={fileInputRef} type="file" accept=".json" onChange={loadParams} className="hidden" />

      {/* HEADER */}
      <header className="flex justify-between items-center mb-6 border-b-2 border-white pb-4 font-black text-xl shrink-0">
        <div className="flex items-center gap-3"><Box size={24} /> Dryguny // Lab</div>
        <div className="flex gap-4">
          <button onClick={() => setTab('organizer')} className={`px-5 py-1 border-2 border-white font-bold transition-all ${tab === 'organizer' ? 'bg-white text-black' : 'hover:bg-zinc-800'}`}>Organizer</button>
          <button onClick={() => setTab('vases')} className={`px-5 py-1 border-2 border-white font-bold transition-all ${tab === 'vases' ? 'bg-white text-black' : 'hover:bg-zinc-800'}`}>Vase</button>
          <button onClick={exportSTL} className="bg-blue-600 text-white px-8 py-1 border-2 border-white font-black hover:bg-blue-700 transition-all active:scale-95">Export STL</button>
        </div>
      </header>

      <div className="flex flex-1 gap-6 min-h-0">

        {/* VIEWPORT */}
        <div className="flex-1 border-4 border-white bg-zinc-900 relative">
          <Canvas shadows>
            <PerspectiveCamera makeDefault position={[220, 220, 220]} />
            <OrbitControls />
            <ambientLight intensity={0.6} />
            <pointLight position={[100, 200, 100]} castShadow intensity={2} />
            <Suspense fallback={null}>
              <group ref={groupRef}>
                {tab === 'organizer' ? (
                  <OrganizerModel cols={cols} rows={rows} cellW={cellW} cellL={cellL} depth={depth} wall={wall} backWall={backWall} radius={radius} color={mainColor} hasBottom={hasBottom} wireframe={wireframe} />
                ) : (
                  <VaseModel height={vH} rBase={vRB} rMid={vRM} rTop={vRT} midPos={vMP} twistGlobal={vTwistG} twistBase={vTwistB} twistTop={vTwistT} useTwistB={vUseTB} useTwistT={vUseTT} segments={vSeg} shapeType={vType} color={mainColor} vWall={vWall} vBottom={vBottom} patternStr={vPattern} wireframe={wireframe} />
                )}
              </group>
              {showGrid && <gridHelper args={[600, 60, '#252525', '#1a1a1a']} position={[0, -2, 0]} />}
              <Environment preset="studio" />
            </Suspense>
          </Canvas>

          {/* Viewport controls — top left */}
          <div className="absolute top-3 left-3 flex gap-2 z-10">
            <button
              onClick={() => setWireframe(!wireframe)}
              className={`px-3 py-1 text-[11px] font-black border-2 border-white transition-all ${wireframe ? 'bg-white text-black' : 'bg-black/70 text-white hover:bg-zinc-800'}`}
            >
              WIRE
            </button>
            <button
              onClick={() => setShowGrid(!showGrid)}
              className={`px-3 py-1 text-[11px] font-black border-2 border-white transition-all ${showGrid ? 'bg-white text-black' : 'bg-black/70 text-white hover:bg-zinc-800'}`}
            >
              GRID
            </button>
          </div>

          {/* Dimensions + weight — bottom left */}
          <div className="absolute bottom-3 left-3 flex items-center gap-4 bg-black/85 border border-white/40 px-4 py-2 text-[11px] font-mono z-10">
            <span className="text-zinc-500">SIZE</span>
            <span className="text-white font-bold">{dims.w} × {dims.l} × {dims.d} <span className="text-zinc-500">mm</span></span>
            <span className="text-zinc-600">|</span>
            <span className="text-zinc-500">~</span>
            <span className="text-yellow-400 font-bold">{weight}<span className="text-zinc-500 ml-0.5">g</span></span>
            <span className="text-zinc-600 text-[9px]">PLA</span>
          </div>
        </div>

        {/* SIDEBAR */}
        <aside className="w-[400px] overflow-y-auto border-4 border-white p-6 bg-black flex flex-col gap-6 scrollbar-hide">

          {/* PRESETS */}
          <div>
            <div className="text-[10px] font-black text-zinc-500 mb-2 tracking-widest">PRESETS</div>
            <div className="flex flex-wrap gap-2">
              {Object.keys(activePresets).map(name => (
                <button
                  key={name}
                  onClick={() => applyPreset(name)}
                  className="px-3 py-1 text-[11px] font-black border border-zinc-600 hover:border-white hover:bg-zinc-800 transition-all"
                >
                  {name}
                </button>
              ))}
            </div>
          </div>

          <div className="border-t border-zinc-800" />

          {/* VASE CONTROLS */}
          {tab === 'vases' ? (
            <>
              <div className="grid grid-cols-4 border-2 border-white overflow-hidden shrink-0">
                {['square', 'wave', 'star', 'circle'].map(s => (
                  <button key={s} onClick={() => setVType(s)} className={`py-1 font-black text-xs border-r border-white last:border-0 ${vType === s ? 'bg-white text-black' : 'bg-black text-white hover:bg-zinc-800'}`}>{s}</button>
                ))}
              </div>

              <div className="space-y-4">
                <div className="p-4 border-2 border-white bg-blue-900/40">
                  <div className="flex justify-between font-black mb-1 text-cyan-400"><span><Activity size={14} className="inline mr-2" />WEAVE / PATTERN STR</span><span>{vPattern.toFixed(2)}</span></div>
                  <input type="range" min={0} max={0.4} step={0.01} value={vPattern} onChange={e => setVPattern(+e.target.value)} className="w-full accent-cyan-400" />
                </div>

                <div className="p-4 border-2 border-white bg-zinc-800">
                  <div className="flex justify-between font-black mb-1 text-yellow-400"><span><RotateCcw size={14} className="inline mr-2" />GLOBAL SPIRAL TWIST</span><span>{vTwistG.toFixed(1)}</span></div>
                  <input type="range" min={-10} max={10} step={0.1} value={vTwistG} onChange={e => setVTwistG(+e.target.value)} className="w-full accent-yellow-400" />
                </div>

                <div className="p-3 border border-white bg-zinc-900">
                  <div className="flex justify-between font-black mb-1 uppercase"><span><ArrowUpDown size={14} className="inline mr-2" />Mid Position</span><span>{vMP}</span></div>
                  <input type="range" min={0.1} max={0.9} step={0.01} value={vMP} onChange={e => setVMP(+e.target.value)} className="w-full accent-white" />
                </div>

                <div className="p-3 border border-white bg-zinc-900">
                  <div className="flex justify-between font-black mb-1 uppercase"><span><Layers size={14} className="inline mr-2" />Ребра</span><span>{vSeg}</span></div>
                  <input type="range" min={3} max={120} step={1} value={vSeg} onChange={e => setVSeg(+e.target.value)} className="w-full accent-white" />
                </div>

                <div className="grid grid-cols-2 gap-3 p-2 border border-white">
                  <div className="p-1">
                    <div className="flex justify-between mb-1 cursor-pointer" onClick={() => setVUseTB(!vUseTB)}><span className="text-[10px]"><Wind size={12} className="inline mr-1" />Local Bottom</span>{vUseTB ? <CheckSquare size={16} /> : <Square size={16} />}</div>
                    <input type="range" min={-5} max={5} step={0.1} value={vTwistB} disabled={!vUseTB} onChange={e => setVTwistB(+e.target.value)} className="w-full accent-white disabled:opacity-20" />
                  </div>
                  <div className="p-1">
                    <div className="flex justify-between mb-1 cursor-pointer" onClick={() => setVUseTT(!vUseTT)}><span className="text-[10px]"><Wind size={12} className="inline mr-1" />Local Top</span>{vUseTT ? <CheckSquare size={16} /> : <Square size={16} />}</div>
                    <input type="range" min={-5} max={5} step={0.1} value={vTwistT} disabled={!vUseTT} onChange={e => setVTwistT(+e.target.value)} className="w-full accent-white disabled:opacity-20" />
                  </div>
                </div>

                {[
                  { l: 'Base Radius', v: vRB, s: setVRB, m: 5, x: 150 },
                  { l: 'Mid Radius', v: vRM, s: setVRM, m: 5, x: 150 },
                  { l: 'Top Radius', v: vRT, s: setVRT, m: 5, x: 150 },
                  { l: 'Height', v: vH, s: setVH, m: 40, x: 400 },
                  { l: 'Bottom Thick', v: vBottom, s: setVBottom, m: 1, x: 30 },
                  { l: 'Wall Thick', v: vWall, s: setVWall, m: 0.8, x: 10, st: 0.1 }
                ].map(p => (
                  <div key={p.l} className="px-1 border-b border-zinc-800 pb-1 last:border-0">
                    <div className="flex justify-between font-bold text-[10px] mb-1 uppercase"><span>{p.l}</span><span>{p.v}</span></div>
                    <input type="range" min={p.m} max={p.x} step={p.st || 1} value={p.v} onChange={e => p.s(+e.target.value)} className="w-full accent-white h-1" />
                  </div>
                ))}
              </div>
            </>
          ) : (
            /* ORGANIZER CONTROLS */
            <>
              <div className="flex items-center justify-between p-3 border border-white bg-zinc-900 cursor-pointer" onClick={() => setHasBottom(!hasBottom)}>
                <span className="font-black">Show Bottom Wall</span>{hasBottom ? <CheckSquare size={18} /> : <Square size={18} />}
              </div>

              <div className="space-y-4">
                {[
                  { l: 'Cols', v: cols, s: setCols, m: 1, x: 12 },
                  { l: 'Rows', v: rows, s: setRows, m: 1, x: 12 },
                  { l: 'Cell Width', v: cellW, s: setCellW, m: 10, x: 150 },
                  { l: 'Cell Length', v: cellL, s: setCellL, m: 10, x: 150 },
                  { l: 'Depth', v: depth, s: setDepth, m: 5, x: 250 },
                  { l: 'Wall', v: wall, s: setWall, m: 0.8, x: 10, st: 0.1 },
                  { l: 'Bottom Thick', v: backWall, s: setBackWall, m: 0.4, x: 20, st: 0.1 },
                  { l: 'Radius', v: radius, s: setRadius, m: 0, x: 40 }
                ].map(p => (
                  <div key={p.l} className="px-1 border-b border-zinc-800 pb-1 last:border-0">
                    <div className="flex justify-between font-bold text-[10px] mb-1 uppercase"><span>{p.l}</span><span>{p.v}</span></div>
                    <input type="range" min={p.m} max={p.x} step={p.st || 1} value={p.v} onChange={e => p.s(+e.target.value)} className="w-full accent-white h-1" />
                  </div>
                ))}
              </div>
            </>
          )}

          {/* BOTTOM: COLOR + SAVE/LOAD/RESET */}
          <div className="mt-auto pt-6 border-t-2 border-white flex flex-col gap-3">
            <div className="flex justify-between mb-1 font-black text-sm uppercase"><span>Material_Color</span><MousePointer2 size={12} /></div>
            <input type="color" value={mainColor} onChange={e => setMainColor(e.target.value)} className="w-full h-12 border-2 border-white cursor-pointer bg-black" />

            <div className="grid grid-cols-3 gap-2 mt-1">
              <button
                onClick={saveParams}
                className="flex items-center justify-center gap-2 py-2 border-2 border-white font-black text-[11px] hover:bg-zinc-800 transition-all"
              >
                <Save size={12} /> SAVE
              </button>
              <button
                onClick={() => fileInputRef.current?.click()}
                className="flex items-center justify-center gap-2 py-2 border-2 border-white font-black text-[11px] hover:bg-zinc-800 transition-all"
              >
                <Upload size={12} /> LOAD
              </button>
              <button
                onClick={() => tab === 'vases' ? applyVasePreset('Default') : applyOrgPreset('Default')}
                className="flex items-center justify-center gap-2 py-2 border-2 border-zinc-600 font-black text-[11px] text-zinc-400 hover:border-white hover:text-white transition-all"
              >
                <RefreshCw size={12} /> RESET
              </button>
            </div>
          </div>
        </aside>
      </div>
    </main>
  );
}
