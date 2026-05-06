"use client";
import React, { useState, useMemo } from 'react';
import { 
  Calculator, Layers, Zap, Clock, ChevronLeft, ArrowRight
} from 'lucide-react';

// Стилі неоморфізму (світла тема зі скрінів)
const neuOutset = "bg-[#ebf0f7] shadow-[6px_6px_12px_#ced4da,-6px_-6px_12px_#ffffff]";
const neuInset = "bg-[#ebf0f7] shadow-[inset_4px_4px_8px_#ced4da,inset_-4px_-4px_8px_#ffffff]";
const neuButton = "active:shadow-[inset_2px_2px_5px_#ced4da,inset_-2px_-2px_5px_#ffffff] transition-all duration-200";

export default function DrygunyLab() {
  const [showCalc, setShowCalc] = useState(false);
  const [currency] = useState('₴');

  // Дані для розрахунку (за замовчуванням для Bambu Lab)
  const [weight, setWeight] = useState(100);
  const [plasticPrice, setPlasticPrice] = useState(800);
  const [isAMS, setIsAMS] = useState(false);
  const [switches, setSwitches] = useState(0);
  const [flush, setFlush] = useState(0.6);
  const [printTime, setPrintTime] = useState(2);
  const [elecTariff, setElecTariff] = useState(4.32);

  // Логіка підрахунку
  const totalCost = useMemo(() => {
    const plasticCost = (weight / 1000) * plasticPrice;
    const amsWasteCost = isAMS ? ((switches * flush) / 1000) * plasticPrice : 0;
    const energyCost = (printTime * 0.12) * elecTariff; // 0.12 кВт середнє споживання
    return (plasticCost + amsWasteCost + energyCost).toFixed(2);
  }, [weight, plasticPrice, isAMS, switches, flush, printTime, elecTariff]);

  if (!showCalc) {
    return (
      <div className="min-h-screen bg-[#ebf0f7] flex flex-col items-center justify-center p-6 text-slate-800">
        <h1 className="text-5xl font-black mb-2 tracking-tighter">DRYGUNY <span className="text-blue-600">LAB</span></h1>
        <p className="text-slate-400 font-bold uppercase tracking-widest text-xs mb-12">Твій центр утиліт для 3D-друку</p>
        
        <button 
          onClick={() => setShowCalc(true)}
          className={`${neuOutset} ${neuButton} group px-12 py-8 rounded-[2.5rem] flex items-center gap-6 hover:scale-105 transition-transform`}
        >
          <div className={`${neuInset} p-4 rounded-2xl text-blue-600`}>
            <Calculator size={32} />
          </div>
          <div className="text-left">
            <div className="text-2xl font-black">Калькулятор</div>
            <div className="text-sm text-slate-400 font-bold flex items-center">Відкрити інструмент <ArrowRight size={16} className="ml-2 group-hover:translate-x-2 transition-transform" /></div>
          </div>
        </button>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#ebf0f7] p-6 text-slate-700 font-sans">
      <header className="max-w-xl mx-auto mb-10">
        <button onClick={() => setShowCalc(false)} className="flex items-center gap-2 text-slate-400 font-black text-xs uppercase tracking-widest hover:text-blue-600 transition-colors">
          <ChevronLeft size={16} /> Назад на головну
        </button>
      </header>

      <main className="max-w-xl mx-auto space-y-6">
        <h2 className="text-3xl font-black text-center mb-8">Калькулятор собівартості</h2>

        {/* Блок Матеріалу */}
        <section className={`${neuOutset} p-8 rounded-[2.5rem] space-y-6`}>
          <div className="flex items-center gap-2 text-[10px] font-black text-slate-400 uppercase tracking-[0.2em]"><Layers size={14} /> Матеріал</div>
          
          <div className="grid grid-cols-2 gap-6">
            <div className="space-y-2">
              <label className="text-xs font-bold ml-2 text-slate-500">Вага деталі (г)</label>
              <input type="number" value={weight} onChange={e => setWeight(+e.target.value)} className={`${neuInset} w-full p-4 rounded-2xl border-none outline-none font-bold text-blue-600`} />
            </div>
            <div className="space-y-2">
              <label className="text-xs font-bold ml-2 text-slate-500">Ціна котушки ({currency})</label>
              <input type="number" value={plasticPrice} onChange={e => setPlasticPrice(+e.target.value)} className={`${neuInset} w-full p-4 rounded-2xl border-none outline-none font-bold text-blue-600`} />
            </div>
          </div>

          <div 
            onClick={() => setIsAMS(!isAMS)}
            className={`flex justify-between items-center p-4 rounded-2xl cursor-pointer transition-all ${isAMS ? neuInset : 'hover:opacity-80'}`}
          >
            <span className="font-black text-sm">Використовую AMS</span>
            <div className={`w-12 h-6 rounded-full relative ${isAMS ? 'bg-blue-500' : 'bg-slate-300'}`}>
              <div className={`absolute top-1 w-4 h-4 bg-white rounded-full transition-all ${isAMS ? 'left-7' : 'left-1'}`} />
            </div>
          </div>

          {isAMS && (
            <div className="grid grid-cols-2 gap-6 animate-in slide-in-from-top-2 duration-300">
              <div className="space-y-1">
                <label className="text-[10px] font-bold text-slate-400 uppercase ml-2">Змін кольору</label>
                <input type="number" value={switches} onChange={e => setSwitches(+e.target.value)} className={`${neuInset} w-full p-3 rounded-xl border-none outline-none font-bold`} />
              </div>
              <div className="space-y-1">
                <label className="text-[10px] font-bold text-slate-400 uppercase ml-2">Злив (г/зміна)</label>
                <input type="number" value={flush} onChange={e => setFlush(+e.target.value)} className={`${neuInset} w-full p-3 rounded-xl border-none outline-none font-bold`} />
              </div>
            </div>
          )}
        </section>

        {/* Блок Ресурсів */}
        <section className={`${neuOutset} p-8 rounded-[2.5rem] space-y-6`}>
          <div className="flex items-center gap-2 text-[10px] font-black text-slate-400 uppercase tracking-[0.2em]"><Zap size={14} /> Ресурси</div>
          <div className="grid grid-cols-2 gap-6">
            <div className="space-y-2">
              <label className="text-xs font-bold ml-2 text-slate-500">Час друку (год)</label>
              <input type="number" value={printTime} onChange={e => setPrintTime(+e.target.value)} className={`${neuInset} w-full p-4 rounded-2xl border-none outline-none font-bold text-blue-600`} />
            </div>
            <div className="space-y-2">
              <label className="text-xs font-bold ml-2 text-slate-500">Тариф світла</label>
              <input type="number" value={elecTariff} onChange={e => setElecTariff(+e.target.value)} className={`${neuInset} w-full p-4 rounded-2xl border-none outline-none font-bold text-blue-600`} />
            </div>
          </div>
        </section>

        {/* Підсумок */}
        <div className={`${neuInset} p-10 rounded-[3rem] text-center`}>
          <span className="text-[10px] font-black uppercase text-slate-400 tracking-[0.3em] mb-3 block">Собівартість друку</span>
          <div className="text-6xl font-black text-slate-800 tracking-tighter">
            {totalCost}<span className="text-blue-600 ml-2 text-3xl">{currency}</span>
          </div>
        </div>
      </main>
    </div>
  );
}
