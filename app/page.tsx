"use client";
import React, { useState, useMemo, useEffect } from 'react';
import { 
  Calculator, Settings, Plus, Box, Clock, X, FileText, 
  Trash2, Save, FolderOpen, Clipboard, RefreshCw, ChevronDown, Check, Layers
} from 'lucide-react';

// --- Types & Constants (Твоя логіка) ---
interface Printer { id: number; name: string; power: number; wear: number; active: boolean; }
interface PrinterPreset { name: string; power: number; wear: number; }
interface NewPrinterForm { preset: string; name: string; power: number; wear: number; }
interface Calculations { resources: string; beforeDefect: string; defectCost: string; totalCost: string; clientPrice: string; }
type TabType = 'cost' | 'price' | 'file';

const PRINTER_PRESETS: PrinterPreset[] = [
  { name: "Bambu Lab A1", power: 95, wear: 15 },
  { name: "Bambu Lab A1 mini", power: 80, wear: 15 },
  { name: "Bambu Lab P1P / P1S", power: 110, wear: 25 },
  { name: "Bambu Lab X1-Carbon", power: 110, wear: 30 },
  { name: "Creality K1 / Max", power: 100, wear: 20 },
  { name: "Anycubic Kobra 3", power: 100, wear: 15 },
  { name: "Prusa MK4S", power: 80, wear: 17 }
];

// --- Styled Sub-components ---
const InputField = ({ label, value, onChange, type = "number", step = "1", suffix = "" }: any) => (
  <div className="space-y-1.5">
    <label className="block text-[11px] font-bold uppercase tracking-wider text-slate-400 ml-1">
      {label}
    </label>
    <div className="relative">
      <input
        type={type}
        step={step}
        value={value}
        onChange={(e) => onChange(type === "number" ? Number(e.target.value) : e.target.value)}
        className="w-full px-4 py-3 bg-slate-50/50 dark:bg-slate-900/50 border border-slate-200 dark:border-slate-700 rounded-2xl focus:ring-4 focus:ring-blue-500/10 focus:border-blue-500 outline-none transition-all text-slate-700 dark:text-slate-200 font-medium"
      />
      {suffix && <span className="absolute right-4 top-1/2 -translate-y-1/2 text-[10px] font-black text-slate-400 bg-slate-100 dark:bg-slate-800 px-1.5 py-0.5 rounded-md">{suffix}</span>}
    </div>
  </div>
);

const Card = ({ children, title, icon: Icon, colorClass, badge }: any) => (
  <div className="bg-white/80 dark:bg-slate-800/80 backdrop-blur-xl border border-white dark:border-slate-700/50 rounded-[2.5rem] p-6 shadow-xl shadow-slate-200/40 dark:shadow-none mb-6 relative overflow-hidden">
    <div className="flex items-center justify-between mb-6">
      <div className="flex items-center gap-3">
        <div className={`p-2.5 rounded-2xl ${colorClass || 'bg-blue-50 text-blue-600'}`}>
          <Icon size={18} />
        </div>
        <h3 className="font-bold text-slate-800 dark:text-slate-100">{title}</h3>
      </div>
      {badge && <span className="text-[10px] font-black uppercase tracking-widest text-slate-400 bg-slate-100 dark:bg-slate-800 px-3 py-1 rounded-full">{badge}</span>}
    </div>
    {children}
  </div>
);

export default function Calculator3D() {
  const [activeTab, setActiveTab] = useState<TabType>('cost');
  const [showPrinterModal, setShowPrinterModal] = useState<boolean>(false);
  const [projectMode, setProjectMode] = useState<boolean>(false);
  const [printers, setPrinters] = useState<Printer[]>([{ id: 1, name: "Bambu Lab A1", power: 95, wear: 15, active: true }]);
  const [newPrinter, setNewPrinter] = useState<NewPrinterForm>({ preset: "", name: "Мій принтер", power: 100, wear: 15 });

  // Input States
  const [weight, setWeight] = useState(0);
  const [plasticPrice, setPlasticPrice] = useState(0);
  const [isAMS, setIsAMS] = useState(false);
  const [colorChanges, setColorChanges] = useState(0);
  const [flushPerChange, setFlushPerChange] = useState(0.6);
  const [wasteWeight, setWasteWeight] = useState(0);
  const [primeWeight, setPrimeWeight] = useState(0);
  const [printTime, setPrintTime] = useState(0);
  const [electricityRate, setElectricityRate] = useState(4.32);
  const [packaging, setPackaging] = useState(0);
  const [defectRate, setDefectRate] = useState(0);
  const [markup, setMarkup] = useState(0);

  // Ваша логіка розрахунків
  const calculations = useMemo<Calculations>(() => {
    const activePrinter = printers.find(p => p.active) || printers[0];
    const materialCost = (weight / 1000) * plasticPrice;
    const totalWaste = (isAMS ? (colorChanges * flushPerChange) : 0) + wasteWeight;
    const wasteCost = (totalWaste / 1000) * plasticPrice;
    const primeCost = (primeWeight / 1000) * plasticPrice;
    const electricityCost = printTime * (activePrinter.power / 1000) * electricityRate;
    const wearCost = printTime * activePrinter.wear;
    const resources = materialCost + wasteCost + primeCost + electricityCost + wearCost;
    const beforeDefect = resources + packaging;
    const defectCost = beforeDefect * (defectRate / 100);
    const totalCost = beforeDefect + defectCost;
    const clientPrice = totalCost * (1 + markup / 100);

    return {
      resources: resources.toFixed(2),
      beforeDefect: beforeDefect.toFixed(2),
      defectCost: defectCost.toFixed(2),
      totalCost: totalCost.toFixed(2),
      clientPrice: clientPrice.toFixed(2)
    };
  }, [weight, plasticPrice, isAMS, colorChanges, flushPerChange, wasteWeight, primeWeight, printTime, electricityRate, packaging, defectRate, markup, printers]);

  const addPrinter = () => {
    const printer: Printer = { id: Date.now(), name: newPrinter.name, power: newPrinter.power, wear: newPrinter.wear, active: false };
    setPrinters([...printers, printer]);
    setShowPrinterModal(false);
  };

  return (
    <div className="min-h-screen bg-[#F8FAFC] dark:bg-[#0F172A] text-slate-900 dark:text-slate-100 font-sans selection:bg-blue-100">
      <div className="max-w-xl mx-auto px-6 py-12">
        
        {/* Header */}
        <header className="text-center mb-12">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-blue-600 rounded-[2rem] shadow-2xl shadow-blue-500/40 mb-6 text-white rotate-3">
            <Calculator size={32} strokeWidth={2.5} />
          </div>
          <h1 className="text-4xl font-black tracking-tight mb-3">Dryguny Lab</h1>
          <p className="text-slate-500 dark:text-slate-400 text-sm font-medium">Калькулятор собівартості 3D-печатки</p>
        </header>

        {/* Tabs */}
        <div className="flex bg-slate-200/50 dark:bg-slate-800/50 backdrop-blur-md p-1.5 rounded-[2.5rem] mb-10 border border-white/50 dark:border-slate-700/50">
          {(['cost', 'price', 'file'] as TabType[]).map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`flex-1 py-3.5 rounded-[2rem] text-xs font-black uppercase tracking-widest transition-all ${
                activeTab === tab 
                  ? 'bg-white dark:bg-slate-700 shadow-xl text-blue-600 dark:text-white' 
                  : 'text-slate-500 hover:text-slate-700 dark:hover:text-slate-300'
              }`}
            >
              {tab === 'cost' ? 'Розрахунок' : tab === 'price' ? 'Ціна' : 'Файл'}
            </button>
          ))}
        </div>

        {/* --- CONTENT --- */}
        <div className="animate-in fade-in slide-in-from-bottom-4 duration-700">
          {activeTab === 'cost' && (
            <>
              {/* Printers Area */}
              <Card title="Обладнання" icon={Settings} colorClass="bg-slate-100 text-slate-600" badge={`${printers.length} одиниць`}>
                <div className="space-y-3">
                  {printers.map(p => (
                    <div 
                      key={p.id}
                      onClick={() => setPrinters(printers.map(pr => ({ ...pr, active: pr.id === p.id })))}
                      className={`group flex items-center justify-between p-4 rounded-2xl border-2 transition-all cursor-pointer ${
                        p.active ? 'bg-blue-50/50 border-blue-500/50 dark:bg-blue-900/20' : 'bg-transparent border-slate-100 dark:border-slate-700 hover:border-slate-200'
                      }`}
                    >
                      <div className="flex items-center gap-4">
                        <div className={`w-2 h-2 rounded-full ${p.active ? 'bg-blue-500 animate-pulse' : 'bg-slate-300'}`} />
                        <div>
                          <p className="text-sm font-bold">{p.name}</p>
                          <p className="text-[10px] font-bold text-slate-400 uppercase tracking-tighter">{p.power}W • {p.wear}₴/h</p>
                        </div>
                      </div>
                      <button onClick={(e) => { e.stopPropagation(); if(printers.length > 1) setPrinters(printers.filter(pr => pr.id !== p.id)) }} className="opacity-0 group-hover:opacity-100 p-2 text-slate-400 hover:text-red-500 transition-all">
                        <Trash2 size={14} />
                      </button>
                    </div>
                  ))}
                  <button onClick={() => setShowPrinterModal(true)} className="w-full py-4 border-2 border-dashed border-slate-200 dark:border-slate-700 rounded-2xl text-[11px] font-black uppercase tracking-widest text-slate-400 hover:border-blue-400 hover:text-blue-500 transition-all">
                    + Додати принтер
                  </button>
                </div>
              </Card>

              {/* Project Mode Toggle */}
              <div className={`mb-6 p-1 rounded-[2.5rem] border-2 transition-all ${projectMode ? 'bg-purple-500/5 border-purple-500/20' : 'bg-white border-transparent shadow-sm'}`}>
                <button 
                  onClick={() => setProjectMode(!projectMode)}
                  className={`w-full flex items-center justify-between px-6 py-4 rounded-[2rem] transition-all ${projectMode ? 'bg-purple-600 text-white shadow-lg' : 'bg-transparent text-slate-600 hover:bg-slate-50'}`}
                >
                  <div className="flex items-center gap-3">
                    <Layers size={18} strokeWidth={projectMode ? 3 : 2} />
                    <span className="font-bold text-sm">Режим Проект</span>
                  </div>
                  <div className={`w-10 h-5 rounded-full relative transition-colors ${projectMode ? 'bg-white/20' : 'bg-slate-200'}`}>
                    <div className={`absolute top-1 w-3 h-3 rounded-full bg-white transition-all ${projectMode ? 'right-1' : 'left-1'}`} />
                  </div>
                </button>
              </div>

              {/* Material Section */}
              <Card title="Матеріал" icon={Box} colorClass="bg-orange-100 text-orange-600">
                <div className="grid grid-cols-2 gap-4 mb-6">
                  <InputField label="Вага виробу" value={weight} onChange={setWeight} suffix="g" />
                  <InputField label="Ціна пластику" value={plasticPrice} onChange={setPlasticPrice} suffix="₴/kg" />
                </div>
                <div className="flex items-center justify-between p-4 bg-slate-50 dark:bg-slate-900/50 rounded-2xl border border-slate-100 dark:border-slate-700">
                   <div className="flex items-center gap-3">
                      <div className={`p-1.5 rounded-lg ${isAMS ? 'bg-blue-500 text-white' : 'bg-slate-200 text-slate-400'}`}>
                        <Check size={12} strokeWidth={4} />
                      </div>
                      <span className="text-xs font-bold uppercase tracking-wider text-slate-500">AMS / Мультиколір</span>
                   </div>
                   <input type="checkbox" checked={isAMS} onChange={(e) => setIsAMS(e.target.checked)} className="w-5 h-5 accent-blue-600 cursor-pointer" />
                </div>
                {isAMS && (
                  <div className="grid grid-cols-2 gap-4 mt-4 p-4 bg-blue-50/30 dark:bg-blue-900/10 rounded-2xl border border-blue-100/50 animate-in zoom-in-95">
                    <InputField label="Зміни кольору" value={colorChanges} onChange={setColorChanges} />
                    <InputField label="Злив на зміну" value={flushPerChange} onChange={setFlushPerChange} step="0.1" suffix="g" />
                    <InputField label="Інші відходи" value={wasteWeight} onChange={setWasteWeight} suffix="g" />
                    <InputField label="Prime-башта" value={primeWeight} onChange={setPrimeWeight} suffix="g" />
                  </div>
                )}
              </Card>

              {/* Time Section */}
              <Card title="Час та Енергія" icon={Clock} colorClass="bg-blue-100 text-blue-600">
                <div className="grid grid-cols-2 gap-4">
                  <InputField label="Час друку" value={printTime} onChange={setPrintTime} step="0.1" suffix="h" />
                  <InputField label="Тариф енергії" value={electricityRate} onChange={setElectricityRate} step="0.01" suffix="₴" />
                </div>
              </Card>

              {/* Other Section */}
              <Card title="Додатково" icon={Plus} colorClass="bg-red-100 text-red-600">
                <div className="grid grid-cols-2 gap-4">
                  <InputField label="Упаковка" value={packaging} onChange={setPackaging} suffix="₴" />
                  <InputField label="Відсоток браку" value={defectRate} onChange={setDefectRate} suffix="%" />
                </div>
              </Card>

              {/* MAIN RESULT CARD */}
              <div className="bg-slate-900 dark:bg-blue-600 rounded-[3rem] p-10 text-center text-white shadow-2xl shadow-blue-500/30 relative overflow-hidden group">
                <div className="absolute top-0 right-0 w-48 h-48 bg-white/10 rounded-full -mr-20 -mt-20 blur-3xl group-hover:bg-white/20 transition-all duration-700" />
                <p className="text-[11px] font-black uppercase tracking-[0.3em] opacity-50 mb-4">Собівартість виробу</p>
                <div className="text-7xl font-black tracking-tighter mb-6">
                  {calculations.totalCost} <span className="text-3xl opacity-40 font-light">₴</span>
                </div>
                <div className="grid grid-cols-2 gap-2 max-w-[280px] mx-auto text-[10px] font-bold uppercase tracking-widest opacity-60">
                  <div className="bg-white/10 py-2 rounded-xl">Ресурси: {calculations.resources}₴</div>
                  <div className="bg-white/10 py-2 rounded-xl">Брак: {calculations.defectCost}₴</div>
                </div>
              </div>
            </>
          )}

          {activeTab === 'price' && (
            <div className="animate-in fade-in slide-in-from-right-4 duration-500">
              <Card title="Налаштування ціни" icon={Save} colorClass="bg-green-100 text-green-600">
                <div className="mb-10">
                  <InputField label="Ваша націнка" value={markup} onChange={setMarkup} suffix="%" />
                  <input 
                    type="range" min="0" max="500" value={markup} 
                    onChange={(e) => setMarkup(Number(e.target.value))}
                    className="w-full h-1.5 bg-slate-100 rounded-full appearance-none cursor-pointer accent-green-600 mt-6" 
                  />
                </div>
                
                <div className="bg-green-50 dark:bg-green-900/20 rounded-[2.5rem] p-10 text-center border-2 border-green-100 dark:border-green-800/30">
                  <p className="text-[11px] font-black uppercase tracking-[0.2em] text-green-600 dark:text-green-400 mb-4">Фінальна ціна клієнту</p>
                  <div className="text-7xl font-black text-green-700 dark:text-green-300 mb-8 tracking-tighter">
                    {calculations.clientPrice} <span className="text-3xl opacity-50">₴</span>
                  </div>
                  <button 
                    onClick={() => navigator.clipboard.writeText(calculations.clientPrice)}
                    className="group inline-flex items-center gap-3 px-8 py-4 bg-white dark:bg-slate-800 rounded-2xl text-xs font-black uppercase tracking-widest shadow-xl shadow-green-200/50 dark:shadow-none hover:scale-105 active:scale-95 transition-all"
                  >
                    <Clipboard size={16} className="group-hover:text-green-600" /> Копіювати ціну
                  </button>
                </div>
              </Card>
            </div>
          )}

          {activeTab === 'file' && (
            <div className="border-4 border-dashed border-slate-200 dark:border-slate-700 rounded-[3rem] p-20 text-center hover:border-blue-400 hover:bg-blue-50/30 transition-all group cursor-pointer bg-white/50">
              <div className="w-20 h-20 bg-blue-100 dark:bg-blue-900/20 rounded-[2rem] flex items-center justify-center mx-auto mb-6 group-hover:scale-110 group-hover:rotate-6 transition-all duration-500 shadow-lg shadow-blue-200/50">
                <FileText className="text-blue-600" size={32} />
              </div>
              <h3 className="text-xl font-black mb-2 tracking-tight">Завантажити файл</h3>
              <p className="text-sm text-slate-400 font-medium italic">.3mf, .gcode або .stl</p>
            </div>
          )}
        </div>

        {/* Footer actions */}
        <div className="mt-12 flex justify-center">
          <button 
            onClick={() => { setWeight(0); setPrintTime(0); setMarkup(0); }}
            className="group flex items-center gap-2 px-6 py-3 text-[10px] font-black uppercase tracking-[0.2em] text-slate-400 hover:text-red-500 transition-all"
          >
            <RefreshCw size={14} className="group-hover:rotate-180 transition-transform duration-700" /> Скинути все
          </button>
        </div>
      </div>

      {/* --- PRINTER MODAL (Claude Style) --- */}
      {showPrinterModal && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-md flex items-center justify-center z-50 p-6 animate-in fade-in">
          <div className="bg-white dark:bg-slate-800 rounded-[3rem] p-8 max-w-md w-full shadow-2xl animate-in zoom-in-95 duration-300 border border-white/20">
            <div className="flex justify-between items-center mb-8">
              <h3 className="text-2xl font-black tracking-tight">Новий принтер</h3>
              <button onClick={() => setShowPrinterModal(false)} className="p-2 hover:bg-slate-100 rounded-full transition-colors"><X size={20}/></button>
            </div>
            
            <div className="space-y-5 mb-10">
              <div className="space-y-1.5">
                <label className="text-[11px] font-black uppercase tracking-widest text-slate-400 ml-1">Вибрати пресет</label>
                <div className="relative">
                  <select 
                    className="w-full px-4 py-4 bg-slate-50 dark:bg-slate-900 rounded-2xl outline-none border-2 border-transparent focus:border-blue-500 appearance-none font-bold text-sm"
                    onChange={(e) => {
                      const preset = PRINTER_PRESETS.find(p => p.name === e.target.value);
                      if (preset) setNewPrinter({ preset: preset.name, name: preset.name, power: preset.power, wear: preset.wear });
                    }}
                  >
                    <option value="">— Оберіть модель —</option>
                    {PRINTER_PRESETS.map(p => <option key={p.name} value={p.name}>{p.name}</option>)}
                  </select>
                  <ChevronDown className="absolute right-4 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" size={16} />
                </div>
              </div>
              <InputField label="Кастомна назва" value={newPrinter.name} type="text" onChange={(val: string) => setNewPrinter({...newPrinter, name: val})} />
              <div className="grid grid-cols-2 gap-4">
                <InputField label="Потужність (W)" value={newPrinter.power} onChange={(val: number) => setNewPrinter({...newPrinter, power: val})} />
                <InputField label="Знос (₴/h)" value={newPrinter.wear} onChange={(val: number) => setNewPrinter({...newPrinter, wear: val})} />
              </div>
            </div>

            <div className="flex gap-3">
              <button onClick={() => setShowPrinterModal(false)} className="flex-1 py-4 bg-slate-100 dark:bg-slate-700 rounded-[1.5rem] text-xs font-black uppercase tracking-widest">Відміна</button>
              <button onClick={addPrinter} className="flex-2 py-4 bg-blue-600 text-white rounded-[1.5rem] text-xs font-black uppercase tracking-widest shadow-lg shadow-blue-500/40 hover:scale-105 active:scale-95 transition-all">Зберегти принтер</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
