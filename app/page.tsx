"use client";
import React, { useState, useMemo } from 'react';
import { 
  Calculator, Box, Layers, Zap, Clock, Info, 
  Settings, ChevronRight, Search, LayoutGrid, Palette, Cube
} from 'lucide-react';

// --- ТЕМА ТА СТИЛІ (Neumorphism) ---
const neuOutset = "bg-[#ebf0f7] shadow-[6px_6px_12px_#ced4da,-6px_-6px_12px_#ffffff]";
const neuInset = "bg-[#ebf0f7] shadow-[inset_4px_4px_8px_#ced4da,inset_-4px_-4px_8px_#ffffff]";
const neuButton = "active:shadow-[inset_2px_2px_5px_#ced4da,inset_-2px_-2px_5px_#ffffff] transition-all";

export default function DrygunyLab() {
  const [view, setView] = useState('menu'); // 'menu' або 'calc'
  const [currency, setCurrency] = useState('₴');

  // Данні калькулятора (зі скріншотів)
  const [weight, setWeight] = useState(0);
  const [plasticPrice, setPlasticPrice] = useState(0);
  const [isAMS, setIsAMS] = useState(false);
  const [switches, setSwitches] = useState(0);
  const [flush, setFlush] = useState(0.6);
  const [tower, setTower] = useState(0);
  const [printTime, setPrintTime] = useState(0);
  const [elecTariff, setElecTariff] = useState(4.32);

  const utilities = [
    { id: 'calc', title: 'Калькулятор собівартості', desc: 'Рахує вартість по вазі, часу та електриці. Підтримка AMS.', icon: <Calculator className="text-blue-500" /> },
    { id: 'boxes', title: 'Генератор коробок', desc: 'Gridfinity-сумісні bin\'и та звичайні коробки.', icon: <Box className="text-green-500" /> },
    { id: 'texture', title: 'Текстурування STL', desc: 'Рельєфний узор на STL: цегла, шестигранники.', icon: <Palette className="text-purple-500" /> },
    { id: 'voxel', title: 'Вокселізатор', desc: 'Перетворення STL у кубики.', icon: <LayoutGrid className="text-orange-500" /> },
    { id: 'vases', title: 'Генератор ваз', desc: 'Створення унікальних ваз у браузері.', icon: <Cube className="text-cyan-500" /> },
  ];

  return (
    <div className="min-h-screen bg-[#ebf0f7] text-slate-700 p-6 font-sans">
      
      {/* HEADER */}
      <header className="max-w-6xl mx-auto flex justify-between items-center mb-10">
        <div className="flex items-center gap-4 cursor-pointer" onClick={() => setView('menu')}>
          <div className={`${neuOutset} p-3 rounded-2xl`}>
             <div className="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center text-white font-black">D</div>
          </div>
          <div>
            <h1 className="text-xl font-black tracking-tight">DRYGUNY LAB</h1>
            <p className="text-[10px] text-slate-400 font-bold uppercase tracking-widest">3D Printing Utilities</p>
          </div>
        </div>

        <div className={`${neuOutset} flex p-1 rounded-xl`}>
          {['₴', '$', '€'].map(c => (
            <button key={c} onClick={() => setCurrency(c)} className={`px-4 py-1 rounded-lg font-bold text-sm ${currency === c ? 'text-blue-600 ' + neuInset : 'text-slate-400'}`}>{c}</button>
          ))}
        </div>
      </header>

      <main className="max-w-5xl mx-auto">
        
        {view === 'menu' ? (
          <>
            {/* SEARCH & TITLE */}
            <div className="text-center mb-12">
              <h2 className="text-4xl font-black text-slate-800 mb-6">Утиліти для 3D-друку</h2>
              <div className={`${neuInset} max-w-xl mx-auto flex items-center px-6 py-3 rounded-2xl`}>
                <Search size={20} className="text-slate-400 mr-3" />
                <input type="text" placeholder="Знайти утиліту..." className="bg-transparent border-none outline-none w-full text-slate-600 font-medium" />
              </div>
            </div>

            {/* GRID MENU */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
              {utilities.map(u => (
                <div 
                  key={u.id} 
                  onClick={() => u.id === 'calc' && setView('calc')}
                  className={`${neuOutset} p-6 rounded-[2.5rem] hover:scale-[1.02] transition-transform cursor-pointer group relative`}
                >
                  <div className={`${neuInset} w-14 h-14 rounded-2xl flex items-center justify-center mb-6 group-hover:text-blue-500 transition-colors`}>
                    {u.icon}
                  </div>
                  <h3 className="text-lg font-black mb-2 text-slate-800">{u.title}</h3>
                  <p className="text-sm text-slate-500 leading-relaxed mb-6">{u.desc}</p>
                  <div className="flex justify-between items-center">
                    <span className="text-[10px] font-black uppercase text-slate-400 tracking-tighter">Ready to use</span>
                    <div className="text-blue-500 font-bold flex items-center text-sm">Відкрити <ChevronRight size={16} /></div>
                  </div>
                </div>
              ))}
            </div>
          </>
        ) : (
          /* CALCULATOR VIEW (Based on screens) */
          <div className="max-w-2xl mx-auto space-y-8">
            <button onClick={() => setView('menu')} className="text-slate-400 font-bold text-sm flex items-center gap-2 mb-4">← Назад до утиліт</button>
            <h2 className="text-3xl font-black text-center mb-10">Калькулятор собівартості</h2>

            {/* Блок: Матеріал */}
            <div className={`${neuOutset} p-8 rounded-[2.5rem] space-y-6`}>
              <div className="flex items-center gap-2 font-black text-xs uppercase text-slate-400 tracking-widest"><Layers size={16}/> Матеріал</div>
              <div className="grid grid-cols-2 gap-6">
                <div className="space-y-2">
                  <label className="text-xs font-bold ml-2 text-slate-500">Вага виробу (г)</label>
                  <div className={`${neuInset} rounded-2xl flex items-center px-4 py-3 text-blue-600 font-bold`}>
                    <input type="number" value={weight} onChange={e=>setWeight(+e.target.value)} className="bg-transparent w-full outline-none" />
                    <span>г</span>
                  </div>
                </div>
                <div className="space-y-2">
                  <label className="text-xs font-bold ml-2 text-slate-500">Ціна пластику (/кг)</label>
                  <div className={`${neuInset} rounded-2xl flex items-center px-4 py-3 text-blue-600 font-bold`}>
                    <input type="number" value={plasticPrice} onChange={e=>setPlasticPrice(+e.target.value)} className="bg-transparent w-full outline-none" />
                    <span>{currency}</span>
                  </div>
                </div>
              </div>

              {/* AMS SWITCH */}
              <div className="flex justify-between items-center pt-4 border-t border-slate-200/50">
                <span className="font-black text-sm">Багатоколірний друк (AMS)</span>
                <button 
                  onClick={() => setIsAMS(!isAMS)}
                  className={`w-12 h-6 rounded-full relative transition-all ${isAMS ? 'bg-blue-500' : 'bg-slate-300'}`}
                >
                  <div className={`absolute top-1 w-4 h-4 bg-white rounded-full transition-all ${isAMS ? 'left-7' : 'left-1'}`} />
                </button>
              </div>

              {isAMS && (
                <div className="grid grid-cols-2 gap-6 pt-4 animate-in fade-in slide-in-from-top-2">
                  <div className="space-y-1">
                    <label className="text-[10px] font-bold text-slate-400 uppercase">Змін кольору</label>
                    <input type="number" value={switches} onChange={e=>setSwitches(+e.target.value)} className={`${neuInset} w-full rounded-xl p-3 border-none outline-none font-bold`} />
                  </div>
                  <div className="space-y-1">
                    <label className="text-[10px] font-bold text-slate-400 uppercase">Злив (г/зміна)</label>
                    <input type="number" value={flush} onChange={e=>setFlush(+e.target.value)} className={`${neuInset} w-full rounded-xl p-3 border-none outline-none font-bold`} />
                  </div>
                </div>
              )}
            </div>

            {/* Блок: Час та енергія */}
            <div className={`${neuOutset} p-8 rounded-[2.5rem] space-y-6`}>
               <div className="flex items-center gap-2 font-black text-xs uppercase text-slate-400 tracking-widest"><Clock size={16}/> Час + Енергія</div>
               <div className="grid grid-cols-2 gap-6">
                <div className="space-y-2">
                  <label className="text-xs font-bold ml-2 text-slate-500">Час друку (год)</label>
                  <div className={`${neuInset} rounded-2xl flex items-center px-4 py-3 text-blue-600 font-bold`}>
                    <input type="number" value={printTime} onChange={e=>setPrintTime(+e.target.value)} className="bg-transparent w-full outline-none" />
                    <span>год</span>
                  </div>
                </div>
                <div className="space-y-2">
                  <label className="text-xs font-bold ml-2 text-slate-500">Тариф світла</label>
                  <div className={`${neuInset} rounded-2xl flex items-center px-4 py-3 text-blue-600 font-bold`}>
                    <input type="number" value={elecTariff} onChange={e=>setElecTariff(+e.target.value)} className="bg-transparent w-full outline-none" />
                    <span>/кВт</span>
                  </div>
                </div>
              </div>
            </div>

            {/* Підсумок (Footer калькулятора) */}
            <div className={`${neuInset} p-8 rounded-[2.5rem] text-center`}>
              <span className="text-[10px] font-black uppercase text-slate-400 tracking-[0.2em] mb-2 block">Разом собівартість</span>
              <div className="text-5xl font-black text-slate-800">
                {((weight/1000 * plasticPrice) + (isAMS ? (switches * flush)/1000 * plasticPrice : 0) + (printTime * 0.1 * elecTariff)).toFixed(2)}
                <span className="text-blue-500 ml-2">{currency}</span>
              </div>
              <button className={`${neuOutset} ${neuButton} mt-8 px-10 py-4 rounded-2xl font-black text-blue-600 uppercase tracking-widest text-sm`}>
                Скинути все
              </button>
            </div>
          </div>
        )}

      </main>
    </div>
  );
}
