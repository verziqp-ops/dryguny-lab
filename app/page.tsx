"use client";
import React, { useState, Suspense, useRef, useMemo } from 'react';
import { Canvas } from '@react-three/fiber';
import { OrbitControls, PerspectiveCamera, Environment } from '@react-three/drei';
import { Box, CheckSquare, Square } from 'lucide-react';
import * as THREE from 'three';
// @ts-ignore
import { STLExporter } from 'three/examples/jsm/exporters/STLExporter';

// --- ГЕОМЕТРІЯ ОРГАНАЙЗЕРА ---
function OrganizerModel({ cols, rows, cellW, cellL, depth, wall, backWall, radius, color, hasBottom }: any) {
  const totalW = cols * cellW + (cols + 1) * wall;
  const totalL = rows * cellL + (rows + 1) * wall;
  
  const outerShape = useMemo(() => {
    const x = -totalW / 2, y = -totalL / 2;
    const r = Math.min(radius, totalW / 2 - 0.1, totalL / 2 - 0.1);
    const s = new THREE.Shape();
    s.moveTo(x + r, y); s.lineTo(x + totalW - r, y);
    s.absarc(x + totalW - r, y + r, r, -Math.PI / 2, 0, false);
    s.lineTo(x + totalW, y + totalL - r);
    s.absarc(x + totalW - r, y + totalL - r, r, 0, Math.PI / 2, false);
    s.lineTo(x + r, y + totalL);
    s.absarc(x + r, y + totalL - r, r, Math.PI / 2, Math.PI, false);
    s.lineTo(x, y + r);
    s.absarc(x + r, y + r, r, Math.PI, -Math.PI / 2, false);

    const hole = new THREE.Path();
    const ix = x + wall, iy = y + wall, iw = totalW - 2 * wall, il = totalL - 2 * wall;
    const ir = Math.max(0, r - wall);
    hole.moveTo(ix + ir, iy); hole.lineTo(ix + iw - ir, iy);
    hole.absarc(ix + iw - ir, iy + ir, ir, -Math.PI / 2, 0, false);
    hole.lineTo(ix + iw, iy + il - ir);
    hole.absarc(ix + iw - ir, iy + il - ir, ir, 0, Math.PI / 2, false);
    hole.lineTo(ix + ir, iy + il);
    hole.absarc(ix + ir, iy + il - ir, ir, Math.PI / 2, Math.PI, false);
    hole.lineTo(ix, iy + ir);
    hole.absarc(ix + ir, iy + ir, ir, Math.PI, -Math.PI / 2, false);
    s.holes.push(hole);
    return s;
  }, [totalW, totalL, radius, wall]);

  const bottomShape = useMemo(() => {
    const x = -totalW / 2, y = -totalL / 2;
    const r = Math.min(radius, totalW / 2 - 0.1, totalL / 2 - 0.1);
    const s = new THREE.Shape();
    s.moveTo(x + r, y); s.lineTo(x + totalW - r, y);
    s.absarc(x + totalW - r, y + r, r, -Math.PI / 2, 0, false);
    s.lineTo(x + totalW, y + totalL - r);
    s.absarc(x + totalW - r, y + totalL - r, r, 0, Math.PI / 2, false);
    s.lineTo(x + r, y + totalL);
    s.absarc(x + r, y + totalL - r, r, Math.PI / 2, Math.PI, false);
    s.lineTo(x, y + r);
    s.absarc(x + r, y + r, r, Math.PI, -Math.PI / 2, false);
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
            <boxGeometry args={[wall, totalL - 2*wall, depth]} />
            <meshStandardMaterial color={color} />
          </mesh>
        ))}
        {Array.from({ length: rows - 1 }).map((_, r) => (
          <mesh key={`h-${r}`} position={[(totalW-2*wall)/2, (r+1)*(cellL+wall) - wall/2, 0]}>
            <boxGeometry args={[totalW - 2*wall, wall, depth]} />
            <meshStandardMaterial color={color} />
          </mesh>
        ))}
      </group>
    </group>
  );
}

// --- ГЕОМЕТРІЯ ВАЗИ ---
function VaseModel({ height, rBase, rMid, rTop, midPos, twist, segments, shapeType, color, vWall, vBottom }: any) {
  const geometry = useMemo(() => {
    const points = [];
    const segmentsProf = 60;
    
    for (let i = 0; i <= segmentsProf; i++) {
      const t = i / segmentsProf;
      const r = t < midPos 
        ? THREE.MathUtils.lerp(rBase, rMid, Math.sin((t / midPos) * Math.PI / 2))
        : THREE.MathUtils.lerp(rMid, rTop, Math.sin(((t - midPos) / (1 - midPos)) * Math.PI / 2));
      points.push(new THREE.Vector2(r, t * height));
    }
    for (let i = segmentsProf; i >= 0; i--) {
      const t = i / segmentsProf;
      const r = t < midPos 
        ? THREE.MathUtils.lerp(rBase, rMid, Math.sin((t / midPos) * Math.PI / 2))
        : THREE.MathUtils.lerp(rMid, rTop, Math.sin(((t - midPos) / (1 - midPos)) * Math.PI / 2));
      points.push(new THREE.Vector2(Math.max(0.1, r - vWall), Math.max(vBottom, t * height)));
    }
    points.push(new THREE.Vector2(0, vBottom), new THREE.Vector2(0, 0), new THREE.Vector2(rBase, 0));

    const lathe = new THREE.LatheGeometry(points, 128);
    const pos = lathe.attributes.position;
    for (let i = 0; i < pos.count; i++) {
      let x = pos.getX(i), y = pos.getY(i), z = pos.getZ(i);
      const dist = Math.sqrt(x*x + z*z);
      const angle = Math.atan2(z, x);
      
      if (y > 0.1 && dist > 1) {
        let mod = 1;
        if (shapeType === 'star') mod = 1 + Math.abs(Math.cos(angle * (segments / 2))) * 0.3;
        else if (shapeType === 'wave') mod = 1 + Math.sin(angle * segments) * 0.1;
        const currentTwist = (y / height) * twist;
        pos.setXYZ(i, Math.cos(angle + currentTwist) * dist * mod, y, Math.sin(angle + currentTwist) * dist * mod);
      } else if (y < 0.1) pos.setY(i, 0);
    }
    lathe.computeVertexNormals();
    return lathe;
  }, [height, rBase, rMid, rTop, midPos, twist, segments, shapeType, vWall, vBottom]);

  return (
    <mesh geometry={geometry} castShadow receiveShadow>
      <meshStandardMaterial color={color} side={THREE.DoubleSide} roughness={0.3} metalness={0.1} />
    </mesh>
  );
}

export default function Home() {
  const groupRef = useRef<THREE.Group>(null);
  const [tab, setTab] = useState('organizer');
  const [mainColor, setMainColor] = useState('#475569');

  const [cols, setCols] = useState(4);
  const [rows, setRows] = useState(2);
  const [cellW, setCellW] = useState(50);
  const [cellL, setCellL] = useState(55);
  const [depth, setDepth] = useState(105);
  const [wall, setWall] = useState(2);
  const [backWall, setBackWall] = useState(3);
  const [radius, setRadius] = useState(8);
  const [hasBottom, setHasBottom] = useState(true);

  const [vH, setVH] = useState(160);
  const [vRB, setVRB] = useState(40);
  const [vRM, setVRM] = useState(70);
  const [vRT, setVRT] = useState(35);
  const [vMP, setVMP] = useState(0.5); 
  const [vTwist, setVTwist] = useState(1.5);
  const [vSeg, setVSeg] = useState(10);
  const [vType, setVType] = useState('star');
  const [vWall, setVWall] = useState(3);
  const [vBottom, setVBottom] = useState(10);

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
    <main className="h-screen w-full bg-white flex flex-col p-6 overflow-hidden text-slate-900 font-mono italic uppercase text-xs">
      <header className="flex justify-between items-center mb-6 border-b-2 border-black pb-4">
        <div className="flex items-center gap-3"><Box size={24}/> <span className="text-xl font-black">Dryguny // Lab</span></div>
        <div className="flex gap-4">
          <button onClick={() => setTab('organizer')} className={`px-5 py-1 border-2 border-black ${tab === 'organizer' ? 'bg-black text-white' : ''}`}>Organizer</button>
          <button onClick={() => setTab('vases')} className={`px-5 py-1 border-2 border-black ${tab === 'vases' ? 'bg-black text-white' : ''}`}>Vase</button>
          <button onClick={exportSTL} className="bg-black text-white px-6 py-1 border-2 border-black hover:bg-zinc-800 transition-colors">Export STL</button>
        </div>
      </header>

      <div className="flex flex-1 gap-8 min-h-0">
        <div className="flex-[3] border-2 border-black bg-slate-50 relative">
          <Canvas shadows>
            <PerspectiveCamera makeDefault position={[180, 180, 180]} />
            <OrbitControls />
            <ambientLight intensity={0.6} />
            <pointLight position={[100, 200, 100]} castShadow intensity={1} />
            <Suspense fallback={null}>
              <group ref={groupRef}>
                {tab === 'organizer' ? (
                  <OrganizerModel cols={cols} rows={rows} cellW={cellW} cellL={cellL} depth={depth} wall={wall} backWall={backWall} radius={radius} color={mainColor} hasBottom={hasBottom} />
                ) : (
                  <VaseModel height={vH} rBase={vRB} rMid={vRM} rTop={vRT} midPos={vMP} twist={vTwist} segments={vSeg} shapeType={vType} color={mainColor} vWall={vWall} vBottom={vBottom} />
                )}
              </group>
              <Environment preset="studio" />
            </Suspense>
          </Canvas>
        </div>

        <aside className="w-[360px] overflow-y-auto border-2 border-black p-5 bg-white shadow-[4px_4px_0px_0px_rgba(0,0,0,1)]">
          <div className="space-y-5">
            {tab === 'organizer' ? (
              <>
                <div className="flex items-center justify-between p-2 border-2 border-black bg-slate-50 cursor-pointer" onClick={() => setHasBottom(!hasBottom)}>
                  <span className="font-bold">Show Back Wall</span>
                  {hasBottom ? <CheckSquare size={18}/> : <Square size={18}/>}
                </div>
                <div>
                  <label className="flex justify-between font-bold"><span>Cols / Rows</span><span>{cols}x{rows}</span></label>
                  <div className="flex gap-2">
                    <input type="range" min={1} max={12} value={cols} onChange={e => setCols(+e.target.value)} className="w-1/2 accent-black" />
                    <input type="range" min={1} max={12} value={rows} onChange={e => setRows(+e.target.value)} className="w-1/2 accent-black" />
                  </div>
                </div>
                {[
                  {l:'Cell W/L', v:`${cellW}x${cellL}`, s1:setCellW, s2:setCellL, m:10, x:150},
                  {l:'Depth', v:depth, s1:setDepth, m:5, x:250},
                  {l:'Wall', v:wall, s1:setWall, m:0.8, x:10, st:0.1},
                  {l:'Bottom Thick', v:backWall, s1:setBackWall, m:0.4, x:20, st:0.1},
                  {l:'Radius', v:radius, s1:setRadius, m:0, x:40}
                ].map(p => (
                  <div key={p.l}>
                    <label className="flex justify-between font-bold"><span>{p.l}</span><span>{p.v}mm</span></label>
                    {p.s2 ? (
                      <div className="flex gap-2">
                        <input type="range" min={p.m} max={p.x} value={cellW} onChange={e => p.s1(+e.target.value)} className="w-1/2 accent-black" />
                        <input type="range" min={p.m} max={p.x} value={cellL} onChange={e => p.s2(+e.target.value)} className="w-1/2 accent-black" />
                      </div>
                    ) : (
                      <input type="range" min={p.m} max={p.x} step={p.st||1} value={p.v as number} onChange={e => p.s1(+e.target.value)} className="w-full accent-black" />
                    )}
                  </div>
                ))}
              </>
            ) : (
              <>
                <div className="flex border-2 border-black mb-4 overflow-hidden">
                  {['star', 'wave', 'circle'].map(s => (
                    <button key={s} onClick={() => setVType(s)} className={`flex-1 py-1 font-bold ${vType === s ? 'bg-black text-white' : ''}`}>{s}</button>
                  ))}
                </div>
                {[
                  {l:'Vase Wall', v:vWall, s:setVWall, m:0.8, x:15, st:0.1},
                  {l:'Solid Bottom', v:vBottom, s:setVBottom, m:2, x:60},
                  {l:'Height', v:vH, s:setVH, m:40, x:400},
                  {l:'Mid Point Pos', v:vMP, s:setVMP, m:0.05, x:0.95, st:0.01}, 
                  {l:'Base R', v:vRB, s:setVRB, m:5, x:150},
                  {l:'Mid R', v:vRM, s:setVRM, m:5, x:150},
                  {l:'Top R', v:vRT, s:setVRT, m:5, x:150},
                  {l:'Twist', v:vTwist, s:setVTwist, m:-10, x:10, st:0.1},
                  {l:'Segments', v:vSeg, s:setVSeg, m:3, x:64}
                ].map(p => (
                  <div key={p.l}>
                    <label className="flex justify-between font-bold"><span>{p.l}</span><span>{p.v}</span></label>
                    <input type="range" min={p.m} max={p.x} step={p.st||1} value={p.v} onChange={e => p.s(+e.target.value)} className="w-full accent-black" />
                  </div>
                ))}
              </>
            )}
            <div className="pt-4 border-t-2 border-black">
              <label className="font-bold block mb-2">Color Picker</label>
              <input type="color" value={mainColor} onChange={e => setMainColor(e.target.value)} className="w-full h-8 border-2 border-black cursor-pointer" />
            </div>
          </div>
        </aside>
      </div>
    </main>
  );
}
