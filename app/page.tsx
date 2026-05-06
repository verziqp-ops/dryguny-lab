"use client";
import React, { useState, useMemo } from 'react';
import { 
  Search, Calculator, ChevronRight, Heart, 
  Settings, Plus, Zap, Box, Clock, Trash2, Save, X
} from 'lucide-react';

// Стилі Neumorphism
const neuOutset = "bg-[#ebf0f7] shadow-[6px_6px_12px_#ced4da,-6px_-6px_12px_#ffffff]";
const neuInset = "bg-[#ebf0f7] shadow-[inset_4px_4px_8px_#ced4da,inset_-4px_-4px_8px_#ffffff]";
const neuButton = "active:shadow-[inset_2px_2px_5px_#ced4da,inset_-2px_-2px_5px_#ffffff] transition-all";

export default function DrygunyApp() {
  const [view, setView] = useState('main'); // 'main' або 'calc'
  const [calcTab, setCalcTab] = useState('cost'); // 'cost', 'price', 'file'
  const [currency, setCurrency] = useState('₴');

  // Дані калькулятора
  const [weight, setWeight] = useState(0);
  const [plasticPrice, setPlasticPrice] = useState(0);
  const [isAMS, setIsAMS] = useState(false);
  const [switches, setSwitches] = useState(0);
  const [flush, setFlush] = useState(0.6);
  const [printTime, setPrintTime] = useState(0);
  const [elecPrice, setElecPrice] = useState(4.32);
  const [markup, setMarkup] = useState(0);

  // Розрахунок
  const results = useMemo(() => {
    const pCost = (weight / 1000) * plasticPrice;
    const amsCost = isAMS ? ((switches * flush) / 1000) * plasticPrice : 0;
    const eCost = (printTime * 0.1) * elecPrice; // 100Вт = 0.1кВт
    const total = pCost + amsCost + eCost;
    const finalPrice = total + (total * (markup / 100));
    return { total: total.toFixed(2), finalPrice: finalPrice.toFixed(2) };
  }, [weight, plasticPrice, isAMS, switches, flush, printTime, elecPrice, markup]);

  if (view === 'main') {
    return (
      <div className="min-h-screen bg-[#ebf0f7] p-8 text-slate-700">
        <div className="max-w-6xl mx-auto">
          <header className="mb-16 flex justify-between items-start">
            <div>
              <h1 className="text-5xl font-black text-slate-800 mb-4">Утиліти для <span className="text-blue-600">3D-печатників</span></h1>
              <p className="text-slate-400 font-bold max-w-lg">Аналіз STL, генератори моделей, калькулятори. Все безкоштовно та локально.</p>
            </div>
            <div className={`${neuOutset} p-6 rounded-3xl flex items-center gap-4`}>
              <div className={`${neuInset} p-3 rounded-xl text-blue-600`}><Calculator size={24}/></div>
              <div className="text-xs font-black uppercase tracking-tighter">Локально в браузері</div>
            </div>
          </header>

          <div className={`${neuInset} w-full max-w-xl flex items-center px-6 py-4 rounded-full mb-12`}>
            <Search size={20} className="text-slate-300 mr-3" />
            <input placeholder="Знайти утиліту..." className="bg-transparent border-none outline-none w-full font-bold" />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
            <div 
              onClick={() => setView('calc')}
              className={`${neuOutset} p-8 rounded-[2.5rem] cursor-pointer hover:scale-[1.02] transition-all group`}
            >
              <div className="flex justify-between mb-6">
                <div className={`${neuInset} p-3 rounded-xl text-blue-500`}><Calculator size={20}/></div>
                <div className="bg-white/50 px-3 py-1 rounded-full text-[10px] font-black text-green-500 uppercase flex items-center gap-1">
                  <div className="w-1.5 h-1.5 bg-green-500 rounded-full animate-pulse" /> Готово
                </div>
              </div>
              <h3 className="text-xl font-black mb-2">Калькулятор собівартості</h3>
              <p className="text-sm text-slate-400 font-medium mb-8">Рахує ціну пластику, AMS та електрику.</p>
              <div className="flex justify-between items-center text-blue-600 font-black text-sm">
                <span>БІЗНЕС</span>
                <div className="flex items-center gap-1 group-hover:gap-3 transition-all">Відкрити <ChevronRight size={16}/></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#ebf0f7] p-6 text-slate-700">
      <div className="max-w-2xl mx-auto">
        {/* Header Калькулятора */}
        <div className="flex justify-between items-center mb-8">
          <button onClick={() => setView('main')} className="p-3 rounded-full text-slate-400 hover:text-blue-600"><X size={24}/></button>
          <div className="flex gap-2">
            {['₴', '$', '€'].map(c => (
              <button key={c} onClick={() => setCurrency(c)} className={`${currency === c ? neuInset : neuOutset} w-10 h-10 rounded-full font-black text-xs transition-all`}>{c}</button>
            ))}
          </div>
          <div className={`${neuOutset} p-1 rounded-full flex gap-1`}>
            {['cost', 'price', 'file'].map(t => (
              <button 
                key={t}
                onClick={() => setCalcTab(t)}
                className={`px-4 py-2 rounded-full text-[10px] font-black uppercase tracking-widest transition-all ${calcTab === t ? 'bg-blue-600 text-white shadow-inner' : 'text-slate-400'}`}
              >
                {t === 'cost' ? 'Собівартість' : t === 'price' ? 'Ціна' : 'Файл'}
              </button>
            ))}
          </div>
        </div>

        <h2 className="text-2xl font-black text-center mb-8 uppercase tracking-tighter">Калькулятор собівартості 3D-печаті</h2>

        {calcTab === 'cost' && (
          <div className="space-y-6">
            {/* Принтер */}
            <div className={`${neuOutset} p-6 rounded-[2rem]`}>
              <div className="flex justify-between items-center mb-4 text-[10px] font-black text-slate-400 tracking-widest uppercase">
                <div className="flex items-center gap-2">Мої принтери</div>
                <div>0 активних</div>
              </div>
              <div className={`${neuInset} p-4 rounded-2xl flex justify-between items-center opacity-60`}>
                <div>
                  <div className="font-black text-sm">Bambu Lab A1</div>
                  <div className="text-[10px] font-bold">95 Вт · 25 {currency}/ч</div>
                </div>
                <Settings size={16} className="text-slate-400" />
              </div>
              <button className="w-full mt-4 py-3 rounded-2xl border-2 border-dashed border-slate-200 text-slate-400 text-xs font-black uppercase tracking-widest hover:bg-white/20 transition-all">
                + Додати свій принтер
              </button>
            </div>

            {/* Матеріал */}
            <div className={`${neuOutset} p-8 rounded-[2rem] space-y-6`}>
              <div className="flex justify-between items-center">
                <div className="text-[10px] font-black text-slate-400 uppercase tracking-widest flex items-center gap-2"><Box size={14}/> Матеріал</div>
                <div onClick={() => setIsAMS(!isAMS)} className={`w-12 h-6 rounded-full relative cursor-pointer transition-all ${isAMS ? 'bg-blue-500' : 'bg-slate-300'}`}>
                  <div className={`absolute top-1 w-4 h-4 bg-white rounded-full transition-all ${isAMS ? 'left-7' : 'left-1'}`} />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-6">
                <div>
                  <label className="text-[10px] font-black text-slate-400 ml-2">ВЕС ИЗДЕЛИЯ (Г)</label>
                  <input type="number" value={weight} onChange={e=>setWeight(+e.target.value)} className={`${neuInset} w-full p-4 rounded-2xl border-none outline-none font-black text-blue-600 mt-1`} />
                </div>
                <div>
                  <label className="text-[10px] font-black text-slate-400 ml-2">ЦЕНА ПЛАСТИКА (/КГ)</label>
                  <input type="number" value={plasticPrice} onChange={e=>setPlasticPrice(+e.target.value)} className={`${neuInset} w-full p-4 rounded-2xl border-none outline-none font-black text-blue-600 mt-1`} />
                </div>
              </div>
            </div>

            {/* Блок AMS */}
            {isAMS && (
              <div className={`${neuOutset} p-8 rounded-[2rem] space-y-6 animate-in slide-in-from-top-2`}>
                <div className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Мультицвєт / AMS</div>
                <div className="grid grid-cols-2 gap-6">
                  <div>
                    <label className="text-[10px] font-black text-slate-400">КІЛЬКІСТЬ КОЛЬОРІВ</label>
                    <input type="number" value={switches} onChange={e=>setSwitches(+e.target.value)} className={`${neuInset} w-full p-4 rounded-2xl border-none outline-none font-black text-blue-600 mt-1`} />
                  </div>
                  <div>
                    <label className="text-[10px] font-black text-slate-400">ЗЛИВ (Г/ЗМІНА)</label>
                    <input type="number" value={flush} onChange={e=>setFlush(+e.target.value)} className={`${neuInset} w-full p-4 rounded-2xl border-none outline-none font-black text-blue-600 mt-1`} />
                  </div>
                </div>
              </div>
            )}

            {/* Час + Енергія */}
            <div className={`${neuOutset} p-8 rounded-[2rem] space-y-6`}>
              <div className="text-[10px] font-black text-slate-400 uppercase tracking-widest flex items-center gap-2"><Clock size={14}/> Время + энергия</div>
              <div className="grid grid-cols-2 gap-6">
                <div>
                  <label className="text-[10px] font-black text-slate-400">ВРЕМЯ ПЕЧАТИ (Ч)</label>
                  <input type="number" value={printTime} onChange={e=>setPrintTime(+e.target.value)} className={`${neuInset} w-full p-4 rounded-2xl border-none outline-none font-black text-blue-600 mt-1`} />
                </div>
                <div>
                  <label className="text-[10px] font-black text-slate-400">ТАРИФ ЭЛЕКТРИЧЕСТВА</label>
                  <input type="number" value={elecPrice} onChange={e=>setElecPrice(+e.target.value)} className={`${neuInset} w-full p-4 rounded-2xl border-none outline-none font-black text-blue-600 mt-1`} />
                </div>
              </div>
            </div>

            {/* Результат */}
            <div className={`${neuInset} p-10 rounded-[3rem] text-center`}>
               <div className="text-[10px] font-black text-slate-400 tracking-widest uppercase mb-4">Себестоимость</div>
               <div className="text-6xl font-black text-slate-800">{results.total} <span className="text-blue-600 text-3xl">{currency}</span></div>
            </div>
          </div>
        )}

        {calcTab === 'price' && (
          <div className="space-y-6">
            <div className={`${neuOutset} p-8 rounded-[2rem]`}>
              <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Процент наценки (%)</label>
              <input type="range" min="0" max="500" value={markup} onChange={e=>setMarkup(+e.target.value)} className="w-full h-2 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-blue-600 my-8" />
              <div className="text-center font-black text-2xl text-blue-600">{markup}%</div>
            </div>
            <div className={`${neuInset} p-12 rounded-[3rem] text-center`}>
               <div className="text-[10px] font-black text-slate-400 tracking-widest uppercase mb-4">Цена для клиента</div>
               <div className="text-7xl font-black text-blue-600">{results.finalPrice} <span className="text-slate-800 text-3xl">{currency}</span></div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
