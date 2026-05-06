"use client";
import React, { useState, useMemo, useEffect } from 'react';
import { 
  Calculator, Settings, Plus, Box, Clock, X, Sun, Moon, FileText, Tag, MousePointer2
} from 'lucide-react';

export default function DrygunyApp() {
  const [view, setView] = useState('main'); 
  const [calcTab, setCalcTab] = useState('cost');
  const [isDark, setIsDark] = useState(false);
  
  // Дані калькулятора
  const [weight, setWeight] = useState(0);
  const [plasticPrice, setPlasticPrice] = useState(0);
  const [isAMS, setIsAMS] = useState(false);
  const [switches, setSwitches] = useState(0);
  const [flush, setFlush] = useState(0.6);
  const [printTime, setPrintTime] = useState(0);
  const [elecPrice, setElecPrice] = useState(4.32);
  const [markup, setMarkup] = useState(0);

  // Кольори для режимів
  const theme = {
    bg: isDark ? "bg-[#1e2124]" : "bg-[#ebf0f7]",
    text: isDark ? "text-slate-200" : "text-slate-700",
    label: isDark ? "text-slate-500" : "text-slate-400",
    neuOutset: isDark 
      ? "bg-[#1e2124] shadow-[6px_6px_12px_#131517,-6px_-6px_12px_#292d31]" 
      : "bg-[#ebf0f7] shadow-[6px_6px_12px_#ced4da,-6px_-6px_12px_#ffffff]",
    neuInset: isDark
      ? "bg-[#1e2124] shadow-[inset_4px_4px_8px_#131517,inset_-4px_-4px_8px_#292d31]"
      : "bg-[#ebf0f7] shadow-[inset_4px_4px_8px_#ced4da,inset_-4px_-4px_8px_#ffffff]",
  };

  const results = useMemo(() => {
    const pCost = (weight / 1000) * plasticPrice;
    const amsCost = isAMS ? ((switches * flush) / 1000) * plasticPrice : 0;
    const eCost = (printTime * 0.1) * elecPrice;
    const total = pCost + amsCost + eCost;
    const finalPrice = total + (total * (markup / 100));
    return { total: total.toFixed(2), finalPrice: finalPrice.toFixed(2) };
  }, [weight, plasticPrice, isAMS, switches, flush, printTime, elecPrice, markup]);

  if (view === 'main') {
    return (
      <div className={`min-h-screen ${theme.bg} ${theme.text} p-8 transition-colors duration-300`}>
        <div className="max-w-6xl mx-auto">
          <header className="mb-16 flex justify-between items-center">
            <h1 className="text-4xl font-black tracking-tighter uppercase">Dryguny <span className="text-blue-600">Lab</span></h1>
            <button onClick={() => setIsDark(!isDark)} className={`${theme.neuOutset} p-4 rounded-2xl`}>
              {isDark ? <Sun className="text-yellow-400" /> : <Moon className="text-blue-600" />}
            </button>
          </header>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-10">
            <div onClick={() => setView('calc')} className={`${theme.neuOutset} p-10 rounded-[3rem] cursor-pointer hover:scale-[1.02] transition-all`}>
              <Calculator size={40} className="text-blue-600 mb-6" />
              <h2 className="text-2xl font-black mb-2 uppercase">Калькулятор собівартості</h2>
              <p className={`${theme.label} font-bold`}>Точний розрахунок пластику, AMS та амортизації принтера.</p>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className={`min-h-screen ${theme.bg} ${theme.text} p-4 md:p-10 transition-colors duration-300`}>
      <div className="max-w-3xl mx-auto">
        {/* Navigation */}
        <div className="flex flex-wrap gap-4 justify-between items-center mb-10">
          <button onClick={() => setView('main')} className={`${theme.neuOutset} p-4 rounded-2xl`}><X/></button>
          
          <div className={`${theme.neuOutset} p-2 rounded-full flex gap-1`}>
            {[
              { id: 'cost', label: 'Собівартість', icon: <Box size={14}/> },
              { id: 'price', label: 'Ціна', icon: <Tag size={14}/> },
              { id: 'file', label: 'Файл', icon: <FileText size={14}/> }
            ].map(t => (
              <button 
                key={t.id}
                onClick={() => setCalcTab(t.id)}
                className={`px-6 py-3 rounded-full text-[10px] font-black uppercase tracking-widest flex items-center gap-2 transition-all ${calcTab === t.id ? 'bg-blue-600 text-white' : theme.label}`}
              >
                {t.icon} {t.label}
              </button>
            ))}
          </div>
          <button onClick={() => setIsDark(!isDark)} className={`${theme.neuOutset} p-4 rounded-2xl`}>
            {isDark ? <Sun size={20}/> : <Moon size={20}/>}
          </button>
        </div>

        {calcTab === 'cost' && (
          <div className="space-y-8 animate-in fade-in duration-500">
            {/* My Printers Section */}
            <div className={`${theme.neuOutset} p-8 rounded-[2.5rem]`}>
              <div className="flex justify-between text-[10px] font-black uppercase tracking-[0.2em] mb-6 opacity-50">
                <span>Мої принтери</span>
                <span>1 активний</span>
              </div>
              <div className={`${theme.neuInset} p-5 rounded-2xl flex justify-between items-center`}>
                <div className="flex items-center gap-4">
                  <div className="w-3 h-3 bg-green-500 rounded-full shadow-[0_0_10px_rgba(34,197,94,0.5)]" />
                  <div>
                    <div className="font-black text-sm">Bambu Lab A1</div>
                    <div className="text-[10px] font-bold opacity-50">95 Вт · 25 ₴/год</div>
                  </div>
                </div>
                <Settings size={18} className="opacity-30" />
              </div>
              <button className="w-full mt-6 py-4 rounded-2xl border-2 border-dashed border-slate-300 dark:border-slate-700 text-[10px] font-black uppercase tracking-widest opacity-40 hover:opacity-100 transition-all">
                + Додати свій принтер
              </button>
            </div>

            {/* Material Inputs */}
            <div className={`${theme.neuOutset} p-8 rounded-[2.5rem] space-y-8`}>
              <div className="flex justify-between items-center">
                <span className="text-[10px] font-black uppercase tracking-widest opacity-50">Матеріал</span>
                <div onClick={() => setIsAMS(!isAMS)} className={`w-14 h-7 rounded-full p-1 cursor-pointer transition-all ${isAMS ? 'bg-blue-600' : 'bg-slate-300 dark:bg-slate-700'}`}>
                  <div className={`w-5 h-5 bg-white rounded-full transition-all ${isAMS ? 'translate-x-7' : 'translate-x-0'}`} />
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                <div>
                  <label className="text-[10px] font-black opacity-50 ml-2 block mb-2 uppercase">Вага виробу (г)</label>
                  <input type="number" value={weight} onChange={e=>setWeight(+e.target.value)} className={`${theme.neuInset} w-full p-5 rounded-2xl outline-none font-black text-blue-600 bg-transparent`} />
                </div>
                <div>
                  <label className="text-[10px] font-black opacity-50 ml-2 block mb-2 uppercase">Ціна пластику (₴/кг)</label>
                  <input type="number" value={plasticPrice} onChange={e=>setPlasticPrice(+e.target.value)} className={`${theme.neuInset} w-full p-5 rounded-2xl outline-none font-black text-blue-600 bg-transparent`} />
                </div>
              </div>
            </div>

            {isAMS && (
              <div className={`${theme.neuOutset} p-8 rounded-[2.5rem] space-y-8 animate-in slide-in-from-top-4`}>
                <span className="text-[10px] font-black uppercase tracking-widest opacity-50">Багатоколірна печать (AMS)</span>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                  <div>
                    <label className="text-[10px] font-black opacity-50 ml-2 block mb-2 uppercase">Кількість кольорів</label>
                    <input type="number" value={switches} onChange={e=>setSwitches(+e.target.value)} className={`${theme.neuInset} w-full p-5 rounded-2xl outline-none font-black text-blue-600 bg-transparent`} />
                  </div>
                  <div>
                    <label className="text-[10px] font-black opacity-50 ml-2 block mb-2 uppercase">Злив на 1 зміну (г)</label>
                    <input type="number" value={flush} onChange={e=>setFlush(+e.target.value)} className={`${theme.neuInset} w-full p-5 rounded-2xl outline-none font-black text-blue-600 bg-transparent`} />
                  </div>
                </div>
              </div>
            )}

            {/* Energy */}
            <div className={`${theme.neuOutset} p-8 rounded-[2.5rem] space-y-8`}>
              <span className="text-[10px] font-black uppercase tracking-widest opacity-50">Час та енергія</span>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                <div>
                  <label className="text-[10px] font-black opacity-50 ml-2 block mb-2 uppercase">Час друку (год)</label>
                  <input type="number" value={printTime} onChange={e=>setPrintTime(+e.target.value)} className={`${theme.neuInset} w-full p-5 rounded-2xl outline-none font-black text-blue-600 bg-transparent`} />
                </div>
                <div>
                  <label className="text-[10px] font-black opacity-50 ml-2 block mb-2 uppercase">Тариф світла (₴/кВт)</label>
                  <input type="number" value={elecPrice} onChange={e=>setElecPrice(+e.target.value)} className={`${theme.neuInset} w-full p-5 rounded-2xl outline-none font-black text-blue-600 bg-transparent`} />
                </div>
              </div>
            </div>

            {/* Final Cost Display */}
            <div className={`${theme.neuInset} p-12 rounded-[3.5rem] text-center`}>
               <div className="text-[10px] font-black uppercase tracking-[0.3em] opacity-40 mb-4">Собівартість</div>
               <div className="text-7xl font-black tracking-tighter">
                {results.total} <span className="text-blue-600 text-3xl font-bold ml-1">₴</span>
               </div>
            </div>
          </div>
        )}

        {calcTab === 'price' && (
          <div className="space-y-8 animate-in fade-in duration-500">
            <div className={`${theme.neuOutset} p-10 rounded-[3rem]`}>
              <div className="flex justify-between items-center mb-10">
                <span className="text-[10px] font-black uppercase tracking-widest opacity-50">Націнка</span>
                <span className="text-2xl font-black text-blue-600">{markup}%</span>
              </div>
              <input 
                type="range" min="0" max="500" value={markup} 
                onChange={e=>setMarkup(+e.target.value)} 
                className="w-full h-3 bg-slate-200 dark:bg-slate-800 rounded-full appearance-none cursor-pointer accent-blue-600"
              />
            </div>
            <div className={`${theme.neuInset} p-16 rounded-[4rem] text-center`}>
               <div className="text-[10px] font-black uppercase tracking-[0.3em] opacity-40 mb-6">Ціна для клієнта</div>
               <div className="text-8xl font-black tracking-tighter text-blue-600">
                {results.finalPrice} <span className="text-slate-400 text-3xl font-bold ml-2">₴</span>
               </div>
            </div>
          </div>
        )}

        {calcTab === 'file' && (
          <div className={`${theme.neuOutset} p-20 rounded-[4rem] text-center border-4 border-dashed border-slate-200 dark:border-slate-800 animate-pulse`}>
            <MousePointer2 size={48} className="mx-auto mb-6 text-blue-600 opacity-50" />
            <h3 className="text-xl font-black uppercase mb-2">Перетягніть .3mf або .gcode</h3>
            <p className={`${theme.label} font-bold italic`}>Автоматичний аналіз ваги та часу друку скоро з'явиться.</p>
          </div>
        )}
      </div>
    </div>
  );
}
