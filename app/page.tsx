"use client";
import React, { useState, useMemo, useEffect } from 'react';
import { 
  Calculator, Settings, Plus, Box, Clock, X, FileText, Tag, 
  Trash2, Save, FolderOpen, ChevronDown, Check
} from 'lucide-react';

// --- Types ---
interface Printer {
  id: number;
  name: string;
  power: number;
  wear: number;
  active: boolean;
}

interface PrinterPreset {
  name: string;
  power: number;
  wear: number;
}

interface NewPrinterForm {
  preset: string;
  name: string;
  power: number;
  wear: number;
}

interface Calculations {
  resources: string;
  beforeDefect: string;
  defectCost: string;
  totalCost: string;
  clientPrice: string;
}

type TabType = 'cost' | 'price' | 'file';

// --- Constants ---
const PRINTER_PRESETS: PrinterPreset[] = [
  { name: "Anycubic Kobra 3 / S1 Combo", power: 100, wear: 15 },
  { name: "Bambu Lab P2S", power: 110, wear: 20 },
  { name: "Bambu Lab P1P / P1S", power: 110, wear: 25 },
  { name: "Bambu Lab X1-Carbon", power: 110, wear: 30 },
  { name: "Bambu Lab A1", power: 95, wear: 15 },
  { name: "Bambu Lab A1 mini", power: 80, wear: 15 },
  { name: "Flashforge Adventurer 5M/Pro", power: 150, wear: 15 },
  { name: "Creality K1 / K1 Max / K1C", power: 100, wear: 20 },
  { name: "Creality Ender-3 V3", power: 100, wear: 12 },
  { name: "Anycubic Kobra 2 Neo", power: 100, wear: 12 },
  { name: "Anycubic Kobra 2 Max", power: 600, wear: 27 },
  { name: "Elegoo OrangeStorm Giga", power: 1800, wear: 55 },
  { name: "Elegoo Neptune 4 Max", power: 450, wear: 20 },
  { name: "Flashforge (Універсальний)", power: 130, wear: 15 },
  { name: "Prusa MK4S", power: 80, wear: 17 }
];

// --- Sub-components for Optimization ---
const InputField = ({ label, value, onChange, type = "number", step = "1", suffix = "" }: any) => (
  <div>
    <label className="block text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
      {label} {suffix && <span className="ml-1 opacity-60">{suffix}</span>}
    </label>
    <input
      type={type}
      step={step}
      value={value}
      onChange={(e) => onChange(type === "number" ? Number(e.target.value) : e.target.value)}
      className="w-full px-4 py-3 bg-slate-50 dark:bg-slate-700 rounded-xl border-2 border-transparent focus:border-blue-400 dark:focus:border-blue-500 outline-none transition-all"
    />
  </div>
);

export default function Calculator3D() {
  const [activeTab, setActiveTab] = useState<TabType>('cost');
  const [showPrinterModal, setShowPrinterModal] = useState<boolean>(false);
  const [projectMode, setProjectMode] = useState<boolean>(false);
  
  const [printers, setPrinters] = useState<Printer[]>([
    { id: 1, name: "Bambu Lab A1", power: 95, wear: 15, active: true }
  ]);
  
  const [newPrinter, setNewPrinter] = useState<NewPrinterForm>({
    preset: "", name: "Мій принтер", power: 100, wear: 15
  });

  const [weight, setWeight] = useState(0);
  const [plasticPrice, setPlasticPrice] = useState(0);
  const [isAMS, setIsAMS] = useState(false);
  const [colorCount, setColorCount] = useState(2);
  const [colorChanges, setColorChanges] = useState(0);
  const [flushPerChange, setFlushPerChange] = useState(0.6);
  const [wasteWeight, setWasteWeight] = useState(0);
  const [primeWeight, setPrimeWeight] = useState(0);
  const [printTime, setPrintTime] = useState(0);
  const [electricityRate, setElectricityRate] = useState(4.32);
  const [packaging, setPackaging] = useState(0);
  const [defectRate, setDefectRate] = useState(0);
  const [markup, setMarkup] = useState(0);

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
  }, [weight, plasticPrice, isAMS, colorChanges, flushPerChange, wasteWeight, 
      primeWeight, printTime, electricityRate, packaging, defectRate, markup, printers]);

  useEffect(() => {
    if (isAMS) setWasteWeight(colorChanges * flushPerChange);
  }, [isAMS, colorChanges, flushPerChange]);

  const addPrinter = () => {
    const printer: Printer = {
      id: Date.now(),
      name: newPrinter.name,
      power: newPrinter.power,
      wear: newPrinter.wear,
      active: false
    };
    setPrinters([...printers, printer]);
    setShowPrinterModal(false);
    setNewPrinter({ preset: "", name: "Мій принтер", power: 100, wear: 15 });
  };

  const togglePrinter = (id: number) => {
    setPrinters(printers.map(p => ({ ...p, active: p.id === id })));
  };

  const deletePrinter = (id: number) => {
    if (printers.length > 1) {
      setPrinters(printers.filter(p => p.id !== id));
    }
  };

  const resetAll = () => {
    setWeight(0); setPlasticPrice(0); setIsAMS(false); setColorChanges(0);
    setWasteWeight(0); setPrimeWeight(0); setPrintTime(0); setPackaging(0);
    setDefectRate(0); setMarkup(0);
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 to-slate-100 dark:from-slate-900 dark:to-slate-800 text-slate-800 dark:text-slate-200">
      <div className="max-w-4xl mx-auto px-4 py-8">
        
        {/* Header & Tabs */}
        <div className="mb-8">
          <button className="flex items-center gap-2 text-sm text-slate-500 hover:text-slate-700 mb-6">← Всі утиліти</button>
          <div className="text-center mb-8">
            <h1 className="text-3xl md:text-4xl font-bold text-slate-800 dark:text-white mb-3">Калькулятор собівартості 3D-печатки</h1>
            <p className="text-slate-600 dark:text-slate-400 text-sm">Розрахунок за вагою, часом та AMS. Підтримка Bambu Lab та інших систем.</p>
          </div>

          <div className="flex justify-center mb-8">
            <div className="inline-flex bg-white dark:bg-slate-800 rounded-full p-1.5 shadow-lg">
              {(['cost', 'price', 'file'] as TabType[]).map((tab) => (
                <button
                  key={tab}
                  onClick={() => setActiveTab(tab)}
                  className={`px-6 py-2.5 rounded-full text-sm font-semibold transition-all ${
                    activeTab === tab ? 'bg-blue-600 text-white shadow-md' : 'text-slate-600 dark:text-slate-400'
                  }`}
                >
                  {tab === 'cost' ? 'Собівартість' : tab === 'price' ? 'Ціна' : 'Файл'}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* --- COST TAB --- */}
        {activeTab === 'cost' && (
          <div className="space-y-6">
            {/* Printers Section */}
            <div className="bg-white dark:bg-slate-800 rounded-3xl p-6 shadow-lg">
              <div className="flex justify-between items-center mb-4">
                <div className="flex items-center gap-2 text-sm font-semibold text-slate-500"><Settings size={16} /><span>Мої принтери</span></div>
                <span className="text-xs text-slate-400">{printers.filter(p => p.active).length} активних</span>
              </div>
              <div className="space-y-3">
                {printers.map(printer => (
                  <div 
                    key={printer.id}
                    className={`flex items-center justify-between p-4 rounded-2xl transition-all cursor-pointer border-2 ${
                      printer.active ? 'bg-blue-50 dark:bg-blue-900/20 border-blue-200 dark:border-blue-700' : 'bg-slate-50 dark:bg-slate-700/50 border-transparent hover:border-slate-200'
                    }`}
                    onClick={() => togglePrinter(printer.id)}
                  >
                    <div className="flex items-center gap-3">
                      <div className={`w-3 h-3 rounded-full ${printer.active ? 'bg-green-500 shadow-lg' : 'bg-slate-300'}`} />
                      <div>
                        <div className="font-semibold text-sm">{printer.name}</div>
                        <div className="text-xs text-slate-500">{printer.power} Вт · {printer.wear} ₴/год</div>
                      </div>
                    </div>
                    <button onClick={(e) => { e.stopPropagation(); deletePrinter(printer.id); }} className="text-slate-400 hover:text-red-500"><Trash2 size={16} /></button>
                  </div>
                ))}
              </div>
              <button onClick={() => setShowPrinterModal(true)} className="w-full mt-4 py-3 rounded-2xl border-2 border-dashed border-slate-300 text-sm font-semibold text-slate-500 hover:border-blue-400 hover:text-blue-600 transition-all">+ Додати принтер</button>
            </div>

            {/* Project Mode */}
            <div className="bg-white dark:bg-slate-800 rounded-3xl p-6 shadow-lg">
              <div className="flex items-center justify-between">
                <div>
                  <div className="flex items-center gap-2 text-sm font-semibold mb-1"><Box size={16} className="text-purple-500" /><span>Режим Проект</span></div>
                  <p className="text-xs text-slate-500">Збери проект з декількох деталей.</p>
                </div>
                <button onClick={() => setProjectMode(!projectMode)} className={`px-6 py-2.5 rounded-full text-sm font-semibold transition-all ${projectMode ? 'bg-purple-600 text-white' : 'bg-slate-100 dark:bg-slate-700'}`}>
                  {projectMode ? 'Вийти' : 'Включити'}
                </button>
              </div>
              {projectMode && (
                <div className="mt-4 flex gap-3 animate-in fade-in duration-300">
                  <button className="flex-1 py-3 bg-blue-600 text-white rounded-2xl font-semibold text-sm">+ Додати деталь</button>
                  <button className="px-4 py-3 bg-slate-100 dark:bg-slate-700 rounded-2xl"><Save size={18} /></button>
                  <button className="px-4 py-3 bg-slate-100 dark:bg-slate-700 rounded-2xl"><FolderOpen size={18} /></button>
                </div>
              )}
            </div>

            {/* Material & AMS Section */}
            <div className="bg-white dark:bg-slate-800 rounded-3xl p-6 shadow-lg">
              <div className="flex items-center justify-between mb-6">
                <div className="flex items-center gap-2 text-sm font-semibold"><span className="text-orange-500">🎨</span><span>Матеріал</span></div>
                <label className="flex items-center gap-3 cursor-pointer">
                  <span className="text-xs text-slate-500">AMS печать</span>
                  <div onClick={() => setIsAMS(!isAMS)} className={`relative w-12 h-6 rounded-full transition-all ${isAMS ? 'bg-blue-600' : 'bg-slate-300'}`}>
                    <div className={`absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full transition-transform ${isAMS ? 'translate-x-6' : ''}`} />
                  </div>
                </label>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <InputField label="Вага виробу (г)" value={weight} onChange={setWeight} />
                <InputField label="Ціна пластику (₴/кг)" value={plasticPrice} onChange={setPlasticPrice} />
              </div>
            </div>

            {isAMS && (
              <div className="bg-white dark:bg-slate-800 rounded-3xl p-6 shadow-lg animate-in slide-in-from-top-4">
                <div className="flex items-center gap-2 text-sm font-semibold mb-6"><span className="text-pink-500">🎨</span><span>Мультиколір / AMS</span></div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <InputField label="Кольорів" value={colorCount} onChange={setColorCount} />
                  <InputField label="Зміни кольору" value={colorChanges} onChange={setColorChanges} />
                  <InputField label="Злив (г/зміна)" value={flushPerChange} onChange={setFlushPerChange} step="0.1" />
                  <InputField label="Вага відходів (г)" value={wasteWeight} onChange={setWasteWeight} suffix="авто" />
                  <div className="md:col-span-2"><InputField label="Вага prime-башні (г)" value={primeWeight} onChange={setPrimeWeight} /></div>
                </div>
              </div>
            )}

            <div className="bg-white dark:bg-slate-800 rounded-3xl p-6 shadow-lg">
              <div className="flex items-center gap-2 text-sm font-semibold mb-6"><Clock size={16} className="text-blue-500" /><span>Час + енергія</span></div>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <InputField label="Час друку (год)" value={printTime} onChange={setPrintTime} step="0.1" />
                <InputField label="Тариф (₴/кВт·год)" value={electricityRate} onChange={setElectricityRate} step="0.01" />
              </div>
            </div>

            <div className="bg-white dark:bg-slate-800 rounded-3xl p-6 shadow-lg">
              <div className="flex items-center gap-2 text-sm font-semibold mb-6"><span className="text-red-500">📦</span><span>Інше</span></div>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <InputField label="Упаковка (₴)" value={packaging} onChange={setPackaging} />
                <InputField label="Брак (%)" value={defectRate} onChange={setDefectRate} />
              </div>
            </div>

            {/* Cost Results */}
            <div className="bg-gradient-to-br from-blue-50 to-indigo-50 dark:from-slate-800 dark:to-slate-700 rounded-3xl p-8 shadow-xl">
              <div className="text-center mb-6">
                <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">Собівартість</div>
                <div className="text-6xl font-bold text-blue-600 dark:text-blue-400 mb-4">{calculations.totalCost} ₴</div>
              </div>
              <div className="space-y-2 text-sm max-w-xs mx-auto">
                <div className="flex justify-between"><span>Ресурси:</span><span className="font-semibold">{calculations.resources} ₴</span></div>
                <div className="flex justify-between"><span>Брак ({defectRate}%):</span><span className="font-semibold">{calculations.defectCost} ₴</span></div>
              </div>
            </div>
            <button onClick={resetAll} className="w-full py-3 text-sm font-semibold text-slate-500 hover:text-red-600 transition-colors">✕ Скинути всі поля</button>
          </div>
        )}

        {/* --- PRICE TAB --- */}
        {activeTab === 'price' && (
          <div className="space-y-6 animate-in fade-in duration-500">
            <div className="bg-white dark:bg-slate-800 rounded-3xl p-8 shadow-lg text-center">
              <div className="flex items-center justify-center gap-2 text-sm font-semibold mb-6"><span className="text-yellow-500">💵</span><span>Націнка</span></div>
              <input type="number" value={markup} onChange={(e) => setMarkup(Number(e.target.value))} className="w-full max-w-xs px-4 py-3 bg-slate-50 dark:bg-slate-700 rounded-xl text-3xl font-bold text-center mb-6 outline-none border-2 border-transparent focus:border-blue-400" />
              <input type="range" min="0" max="500" value={markup} onChange={(e) => setMarkup(Number(e.target.value))} className="w-full h-2 bg-slate-200 rounded-full appearance-none cursor-pointer accent-blue-600" />
            </div>

            <div className="bg-gradient-to-br from-green-50 to-emerald-50 dark:from-slate-800 dark:to-slate-700 rounded-3xl p-12 shadow-xl text-center">
              <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-4">Ціна для клієнта</div>
              <div className="text-7xl font-bold text-green-600 dark:text-green-400 mb-6">{calculations.clientPrice} ₴</div>
              <button onClick={() => navigator.clipboard.writeText(calculations.clientPrice)} className="inline-flex items-center gap-2 px-6 py-3 bg-white dark:bg-slate-700 rounded-full text-sm font-semibold shadow-md hover:scale-105 transition-all">📋 Копіювати ціну</button>
            </div>
            <button onClick={resetAll} className="w-full py-3 text-sm font-semibold text-slate-500 hover:text-red-600 transition-colors">✕ Скинути всі поля</button>
          </div>
        )}

        {/* --- FILE TAB --- */}
        {activeTab === 'file' && (
          <div className="bg-white dark:bg-slate-800 rounded-3xl p-12 shadow-lg border-4 border-dashed border-slate-300 text-center hover:border-blue-400 transition-all cursor-pointer">
            <div className="inline-flex items-center justify-center w-20 h-20 bg-blue-100 dark:bg-blue-900/30 rounded-full mb-6">
              <FileText size={40} className="text-blue-600" />
            </div>
            <h3 className="text-xl font-bold mb-2">Перетягни .3mf / .gcode сюди</h3>
            <p className="text-sm text-slate-500">або клікни для вибору файлу</p>
          </div>
        )}
      </div>

      {/* --- PRINTER MODAL --- */}
      {showPrinterModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-white dark:bg-slate-800 rounded-3xl p-8 max-w-md w-full shadow-2xl animate-in zoom-in-95 duration-200">
            <h3 className="text-xl font-bold mb-6">Новий принтер</h3>
            <div className="space-y-4 mb-6">
              <div>
                <label className="block text-xs font-medium text-slate-500 mb-2">Вибрати пресет</label>
                <select 
                  className="w-full px-4 py-3 bg-slate-50 dark:bg-slate-700 rounded-xl outline-none border-2 border-transparent focus:border-blue-400"
                  onChange={(e) => {
                    const preset = PRINTER_PRESETS.find(p => p.name === e.target.value);
                    if (preset) setNewPrinter({ preset: preset.name, name: preset.name, power: preset.power, wear: preset.wear });
                  }}
                >
                  <option value="">— вибрати пресет —</option>
                  {PRINTER_PRESETS.map(p => <option key={p.name} value={p.name}>{p.name}</option>)}
                </select>
              </div>
              <InputField label="Назва" value={newPrinter.name} type="text" onChange={(val: string) => setNewPrinter({...newPrinter, name: val})} />
              <InputField label="Потужність (Вт)" value={newPrinter.power} onChange={(val: number) => setNewPrinter({...newPrinter, power: val})} />
              <InputField label="Знос (₴/год)" value={newPrinter.wear} onChange={(val: number) => setNewPrinter({...newPrinter, wear: val})} />
            </div>
            <div className="flex gap-3">
              <button onClick={() => setShowPrinterModal(false)} className="flex-1 py-3 bg-slate-100 dark:bg-slate-700 rounded-xl font-semibold">Відміна</button>
              <button onClick={addPrinter} className="flex-1 py-3 bg-blue-600 text-white rounded-xl font-semibold flex items-center justify-center gap-2"><Save size={18} />Зберегти</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
