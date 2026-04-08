"use client";
import React, { useState, Suspense, useRef, useMemo } from 'react';
import { Canvas } from '@react-three/fiber';
import { OrbitControls, PerspectiveCamera, Environment } from '@react-three/drei';
import { Box, CheckSquare, Square, Wind, ArrowUpDown, Layers, MousePointer2, RotateCcw, Activity } from 'lucide-react';
import * as THREE from 'three';
// @ts-ignore
import { STLExporter } from 'three/examples/jsm/exporters/STLExporter';

// --- MODELS ---
function OrganizerModel({ cols, rows, cellW, cellL, depth, wall, backWall, radius, color, hasBottom }: any) {
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
        <meshStandardMaterial color={color} roughness={0.4} />
      </mesh>
      {hasBottom && (
        <mesh position={[0, 0, -backWall]} castShadow receiveShadow>
          <extrudeGeometry args={[bottomShape, { depth: backWall, bevelEnabled: false }]} />
          <meshStandardMaterial color={color} roughness={0.4} />
        </mesh>
      )}
      <group position={[(-totalW/2 + wall), (-totalL/2 + wall), depth/2]}>
        {Array.from({ length: cols - 1 }).map((_, c) => (
          <mesh key={`v-${c}`} position={[(c+1)*(cellW+wall) - wall/2, (totalL-2*wall)/2, 0]}>
            <boxGeometry args={[wall, totalL - 2*wall, depth]} /><meshStandardMaterial color={color} />
          </mesh>
        ))}
        {Array.from({ length: rows - 1 }).map((_, r) => (
          <mesh key={`h-${r}`} position={[(totalW-2*wall)/2, (r+1)*(cellL+wall) - wall/2, 0]}>
            <boxGeometry args={[totalW - 2*wall, wall, depth]} /><meshStandardMaterial color={color} />
          </mesh>
        ))}
      </group>
    </group>
  );
}

function VaseModel({ height, rBase, rMid, rTop, midPos, twistGlobal, twistBase, twistTop, useTwistB, useTwistT, segments, shapeType, color, vWall, vBottom, patternStr }: any) {
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
      let x = pos.getX(i), y = pos.getY(i), z = pos.getZ(i);
      const dist = Math.sqrt(x*x + z*z);
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
      <meshStandardMaterial color={color} side={THREE.DoubleSide} roughness={0.4} />
    </mesh>
  );
}

// --- MAIN PAGE ---
export default function Home() {
  const groupRef = useRef<THREE.Group>(null);
  const [tab, setTab] = useState('vases');
  const [mainColor, setMainColor] = useState('#1e3a8a');

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

  const exportSTL = () => {
    if (!groupRef.current) return;
    const exporter = new STLExporter();
    const result = exporter.parse(groupRef.current, { binary: true });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(new Blob([result], { type: 'application/octet-stream' }));
    link.download = `Dryguny_${tab}.stl`;
    link.click();
  };

  return (
    <main className="h-screen w-full bg-[#0a0a0a] flex flex-col p-6 overflow-hidden text-white font-sans uppercase tracking-tight selection:bg-white selection:text-black">
      
      <header className="flex justify-between items-center mb-6 border-b-2 border-white pb-4 font-black text-xl shrink-0">
        <div className="flex items-center gap-3"><Box size={24}/> Dryguny // Lab</div>
        <div className="flex gap-4">
          <button onClick={() => setTab('organizer')} className={`px-5 py-1 border-2 border-white font-bold transition-all ${tab === 'organizer' ? 'bg-white text-black' : 'hover:bg-zinc-800'}`}>Organizer</button>
          <button onClick={() => setTab('vases')} className={`px-5 py-1 border-2 border-white font-bold transition-all ${tab === 'vases' ? 'bg-white text-black' : 'hover:bg-zinc-800'}`}>Vase</button>
          <button onClick={exportSTL} className="bg-blue-600 text-white px-8 py-1 border-2 border-white font-black hover:bg-blue-700 transition-all active:scale-95">Export STL</button>
        </div>
      </header>

      <div className="flex flex-1 gap-6 min-h-0">
        <div className="flex-1 border-4 border-white bg-zinc-900 relative">
          <Canvas shadows>
            <PerspectiveCamera makeDefault position={[220, 220, 220]} />
            <OrbitControls />
            <ambientLight intensity={0.6} />
            <pointLight position={[100, 200, 100]} castShadow intensity={2} />
            <Suspense fallback={null}>
              <group ref={groupRef}>
                {tab === 'organizer' ? (
                  <OrganizerModel cols={cols} rows={rows} cellW={cellW} cellL={cellL} depth={depth} wall={wall} backWall={backWall} radius={radius} color={mainColor} hasBottom={hasBottom} />
                ) : (
                  <VaseModel height={vH} rBase={vRB} rMid={vRM} rTop={vRT} midPos={vMP} twistGlobal={vTwistG} twistBase={vTwistB} twistTop={vTwistT} useTwistB={vUseTB} useTwistT={vUseTT} segments={vSeg} shapeType={vType} color={mainColor} vWall={vWall} vBottom={vBottom} patternStr={vPattern} />
                )}
              </group>
              <Environment preset="studio" />
            </Suspense>
          </Canvas>
        </div>

        <aside className="w-[400px] overflow-y-auto border-4 border-white p-6 bg-black flex flex-col gap-6 scrollbar-hide">
          
          {tab === 'vases' ? (
            <>
              <div className="grid grid-cols-4 border-2 border-white overflow-hidden shrink-0">
                {['square', 'wave', 'star', 'circle'].map(s => (
                  <button key={s} onClick={() => setVType(s)} className={`py-1 font-black text-xs border-r border-white last:border-0 ${vType === s ? 'bg-white text-black' : 'bg-black text-white hover:bg-zinc-800'}`}>{s}</button>
                ))}
              </div>

              <div className="space-y-4">
                <div className="p-4 border-2 border-white bg-blue-900/40">
                  <div className="flex justify-between font-black mb-1 text-cyan-400"><span><Activity size={14} className="inline mr-2"/> WEAVE / PATTERN STR</span><span>{vPattern.toFixed(2)}</span></div>
                  <input type="range" min={0} max={0.4} step={0.01} value={vPattern} onChange={e => setVPattern(+e.target.value)} className="w-full accent-cyan-400" />
                </div>

                <div className="p-4 border-2 border-white bg-zinc-800">
                  <div className="flex justify-between font-black mb-1 text-yellow-400"><span><RotateCcw size={14} className="inline mr-2"/> GLOBAL SPIRAL TWIST</span><span>{vTwistG.toFixed(1)}</span></div>
                  <input type="range" min={-10} max={10} step={0.1} value={vTwistG} onChange={e => setVTwistG(+e.target.value)} className="w-full accent-yellow-400" />
                </div>

                <div className="p-3 border border-white bg-zinc-900">
                  <div className="flex justify-between font-black mb-1 uppercase"><span><ArrowUpDown size={14} className="inline mr-2"/> Mid Position</span><span>{vMP}</span></div>
                  <input type="range" min={0.1} max={0.9} step={0.01} value={vMP} onChange={e => setVMP(+e.target.value)} className="w-full accent-white" />
                </div>

                <div className="p-3 border border-white bg-zinc-900">
                   <div className="flex justify-between font-black mb-1 uppercase"><span><Layers size={14} className="inline mr-2"/> Ребра</span><span>{vSeg}</span></div>
                  <input type="range" min={3} max={120} step={1} value={vSeg} onChange={e => setVSeg(+e.target.value)} className="w-full accent-white" />
                </div>

                <div className="grid grid-cols-2 gap-3 p-2 border border-white">
                  <div className="p-1">
                    <div className="flex justify-between mb-1" onClick={() => setVUseTB(!vUseTB)}><span className="text-[10px]"><Wind size={12} className="inline mr-1"/> Local Bottom</span>{vUseTB ? <CheckSquare size={16}/> : <Square size={16}/>}</div>
                    <input type="range" min={-5} max={5} step={0.1} value={vTwistB} disabled={!vUseTB} onChange={e => setVTwistB(+e.target.value)} className="w-full accent-white disabled:opacity-20" />
                  </div>
                  <div className="p-1">
                    <div className="flex justify-between mb-1" onClick={() => setVUseTT(!vUseTT)}><span className="text-[10px]"><Wind size={12} className="inline mr-1"/> Local Top</span>{vUseTT ? <CheckSquare size={16}/> : <Square size={16}/>}</div>
                    <input type="range" min={-5} max={5} step={0.1} value={vTwistT} disabled={!vUseTT} onChange={e => setVTwistT(+e.target.value)} className="w-full accent-white disabled:opacity-20" />
                  </div>
                </div>

                {[
                  {l:'Base Radius', v:vRB, s:setVRB, m:5, x:150},
                  {l:'Mid Radius', v:vRM, s:setVRM, m:5, x:150},
                  {l:'Top Radius', v:vRT, s:setVRT, m:5, x:150},
                  {l:'Height', v:vH, s:setVH, m:40, x:400},
                  {l:'Bottom Thick', v:vBottom, s:setVBottom, m:1, x:30},
                  {l:'Wall Thick', v:vWall, s:setVWall, m:0.8, x:10, st:0.1}
                ].map(p => (
                  <div key={p.l} className="px-1 border-b border-zinc-800 pb-1 last:border-0">
                    <div className="flex justify-between font-bold text-[10px] mb-1 uppercase"><span>{p.l}</span><span>{p.v}</span></div>
                    <input type="range" min={p.m} max={p.x} step={p.st||1} value={p.v} onChange={e => p.s(+e.target.value)} className="w-full accent-white h-1" />
                  </div>
                ))}
              </div>
            </>
          ) : (
            <>
              <div className="flex items-center justify-between p-3 border border-white bg-zinc-900 cursor-pointer" onClick={() => setHasBottom(!hasBottom)}>
                <span className="font-black">Show Bottom Wall</span>{hasBottom ? <CheckSquare size={18}/> : <Square size={18}/>}
              </div>
              
              <div className="space-y-4">
                {[
                  {l:'Cols', v:cols, s:setCols, m:1, x:12},
                  {l:'Rows', v:rows, s:setRows, m:1, x:12},
                  {l:'Depth', v:depth, s:setDepth, m:5, x:250},
                  {l:'Wall', v:wall, s:setWall, m:0.8, x:10, st:0.1},
                  {l:'Bottom Thick', v:backWall, s:setBackWall, m:0.4, x:20, st:0.1},
                  {l:'Radius', v:radius, s:setRadius, m:0, x:40}
                ].map(p => (
                  <div key={p.l} className="px-1 border-b border-zinc-800 pb-1 last:border-0">
                    <div className="flex justify-between font-bold text-[10px] mb-1 uppercase"><span>{p.l}</span><span>{p.v}</span></div>
                    <input type="range" min={p.m} max={p.x} step={p.st||1} value={p.v} onChange={e => p.s(+e.target.value)} className="w-full accent-white h-1" />
                  </div>
                ))}
              </div>
            </>
          )}

          <div className="mt-auto pt-6 border-t-2 border-white flex flex-col gap-2">
            <div className="flex justify-between mb-2 font-black text-sm uppercase"><span>Material_Color</span><MousePointer2 size={12}/></div>
            <input type="color" value={mainColor} onChange={e => setMainColor(e.target.value)} className="w-full h-12 border-2 border-white cursor-pointer bg-black" />
          </div>
        </aside>
      </div>
    </main>
  );
}
