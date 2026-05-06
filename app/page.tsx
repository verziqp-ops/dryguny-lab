"use client";
import React, { useState, useMemo, useEffect } from 'react';
import { 
  Calculator, Settings, Plus, Box, Clock, X, FileText, Tag, 
  Trash2, Save, FolderOpen, ChevronDown, Check
} from 'lucide-react';

// Список принтерів з актуальними цінами для України
const PRINTER_PRESETS = [
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

export default function Calculator3D() {
  const [activeTab, setActiveTab] = useState('cost'); // cost, price, file
  const [showPrinterModal, setShowPrinterModal] = useState(false);
  const [projectMode, setProjectMode] = useState(false);
  
  // Printer state
  const [printers, setPrinters] = useState([
    { id: 1, name: "Bambu Lab A1", power: 95, wear: 15, active: true }
  ]);
  
  // New printer form
  const [newPrinter, setNewPrinter] = useState({
    preset: "",
    name: "Мій принтер",
    power: 100,
    wear: 15
  });

  // Calculator inputs
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

  // Розрахунки
  const calculations = useMemo(() => {
    const activePrinter = printers.find(p => p.active);
    
    // Вартість матеріалу
    const materialCost = (weight / 1000) * plasticPrice;
    
    // Вартість AMS відходів
    const autoWaste = isAMS ? (colorChanges * flushPerChange) : 0;
    const totalWaste = autoWaste + wasteWeight;
    const wasteCost = (totalWaste / 1000) * plasticPrice;
    
    // Вартість prime башти
    const primeCost = (primeWeight / 1000) * plasticPrice;
    
    // Вартість електрики та зносу
    const electricityCost = activePrinter 
      ? (printTime * (activePrinter.power / 1000) * electricityRate)
      : 0;
    
    const wearCost = activePrinter 
      ? (printTime * activePrinter.wear)
      : 0;
    
    // Проміжні суми
    const resources = materialCost + wasteCost + primeCost + electricityCost + wearCost;
    const beforeDefect = resources + packaging;
    const defectCost = beforeDefect * (defectRate / 100);
    
    // Себестоїмість
    const totalCost = beforeDefect + defectCost;
    
    // Ціна для клієнта
    const clientPrice = totalCost + (totalCost * (markup / 100));
    
    return {
      resources: resources.toFixed(2),
      beforeDefect: beforeDefect.toFixed(2),
      defectCost: defectCost.toFixed(2),
      totalCost: totalCost.toFixed(2),
      clientPrice: clientPrice.toFixed(2)
    };
  }, [weight, plasticPrice, isAMS, colorChanges, flushPerChange, wasteWeight, 
      primeWeight, printTime, electricityRate, packaging, defectRate, markup, printers]);

  // Автоматичний розрахунок відходів
  useEffect(() => {
    if (isAMS) {
      const auto = colorChanges * flushPerChange;
      setWasteWeight(auto);
    }
  }, [isAMS, colorChanges, flushPerChange]);

  const addPrinter = () => {
    const selectedPreset = PRINTER_PRESETS.find(p => p.name === newPrinter.preset);
    
    const printer = {
      id: Date.now(),
      name: newPrinter.name,
      power: selectedPreset ? selectedPreset.power : newPrinter.power,
      wear: selectedPreset ? selectedPreset.wear : newPrinter.wear,
      active: false
    };
    
    setPrinters([...printers, printer]);
    setShowPrinterModal(false);
    setNewPrinter({ preset: "", name: "Мій принтер", power: 100, wear: 15 });
  };

  const togglePrinter = (id) => {
    setPrinters(printers.map(p => ({
      ...p,
      active: p.id === id
    })));
  };

  const deletePrinter = (id) => {
    setPrinters(printers.filter(p => p.id !== id));
  };

  const resetAll = () => {
    setWeight(0);
    setPlasticPrice(0);
    setIsAMS(false);
    setColorCount(2);
    setColorChanges(0);
    setFlushPerChange(0.6);
    setWasteWeight(0);
    setPrimeWeight(0);
    setPrintTime(0);
    setElectricityRate(4.32);
    setPackaging(0);
    setDefectRate(0);
    setMarkup(0);
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 to-slate-100 dark:from-slate-900 dark:to-slate-800 text-slate-800 dark:text-slate-200">
      <div className="max-w-4xl mx-auto px-4 py-8">
        
        {/* Header */}
        <div className="mb-8">
          <button className="flex items-center gap-2 text-sm text-slate-500 hover:text-slate-700 dark:hover:text-slate-300 transition-colors mb-6">
            ← Всі утиліти
          </button>
          
          <div className="text-center mb-8">
            <h1 className="text-3xl md:text-4xl font-bold text-slate-800 dark:text-white mb-3">
              Калькулятор собівартості 3D-печатки
            </h1>
            <p className="text-slate-600 dark:text-slate-400 text-sm">
              Розраховує собівартість по вазі/часу/пластику/електриці, аналізує .3mf та .gcode,
              <br />підтримує мультиколір AMS та всі популярні принтери.
            </p>
          </div>

          {/* Tabs */}
          <div className="flex justify-center mb-8">
            <div className="inline-flex bg-white dark:bg-slate-800 rounded-full p-1.5 shadow-lg">
              <button
                onClick={() => setActiveTab('cost')}
                className={`px-6 py-2.5 rounded-full text-sm font-semibold transition-all ${
                  activeTab === 'cost'
                    ? 'bg-blue-600 text-white shadow-md'
                    : 'text-slate-600 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200'
                }`}
              >
                Собівартість
              </button>
              <button
                onClick={() => setActiveTab('price')}
                className={`px-6 py-2.5 rounded-full text-sm font-semibold transition-all ${
                  activeTab === 'price'
                    ? 'bg-blue-600 text-white shadow-md'
                    : 'text-slate-600 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200'
                }`}
              >
                Ціна
              </button>
              <button
                onClick={() => setActiveTab('file')}
                className={`px-6 py-2.5 rounded-full text-sm font-semibold transition-all ${
                  activeTab === 'file'
                    ? 'bg-blue-600 text-white shadow-md'
                    : 'text-slate-600 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200'
                }`}
              >
                Файл
              </button>
            </div>
          </div>
        </div>

        {/* Content */}
        {activeTab === 'cost' && (
          <div className="space-y-6">
            
            {/* Мої принтери */}
            <div className="bg-white dark:bg-slate-800 rounded-3xl p-6 shadow-lg">
              <div className="flex justify-between items-center mb-4">
                <div className="flex items-center gap-2 text-sm font-semibold text-slate-500 dark:text-slate-400">
                  <Settings size={16} />
                  <span>Мої принтери</span>
                </div>
                <span className="text-xs text-slate-400">
                  {printers.filter(p => p.active).length} активних
                </span>
              </div>

              <div className="space-y-3">
                {printers.map(printer => (
                  <div 
                    key={printer.id}
                    className={`relative flex items-center justify-between p-4 rounded-2xl transition-all cursor-pointer ${
                      printer.active
                        ? 'bg-blue-50 dark:bg-blue-900/20 border-2 border-blue-200 dark:border-blue-700'
                        : 'bg-slate-50 dark:bg-slate-700/50 border-2 border-transparent hover:border-slate-200 dark:hover:border-slate-600'
                    }`}
                    onClick={() => togglePrinter(printer.id)}
                  >
                    <div className="flex items-center gap-3">
                      <div className={`w-3 h-3 rounded-full ${
                        printer.active 
                          ? 'bg-green-500 shadow-lg shadow-green-500/50' 
                          : 'bg-slate-300 dark:bg-slate-600'
                      }`} />
                      <div>
                        <div className="font-semibold text-sm">{printer.name}</div>
                        <div className="text-xs text-slate-500 dark:text-slate-400">
                          {printer.power} Вт · {printer.wear} ₴/год
                        </div>
                      </div>
                    </div>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        deletePrinter(printer.id);
                      }}
                      className="text-slate-400 hover:text-red-500 transition-colors"
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                ))}
              </div>

              <button
                onClick={() => setShowPrinterModal(true)}
                className="w-full mt-4 py-3 rounded-2xl border-2 border-dashed border-slate-300 dark:border-slate-600 text-sm font-semibold text-slate-500 dark:text-slate-400 hover:border-blue-400 dark:hover:border-blue-500 hover:text-blue-600 dark:hover:text-blue-400 transition-all"
              >
                + Додати свій принтер
              </button>
            </div>

            {/* Режим Проект */}
            <div className="bg-white dark:bg-slate-800 rounded-3xl p-6 shadow-lg">
              <div className="flex items-center justify-between mb-4">
                <div>
                  <div className="flex items-center gap-2 text-sm font-semibold mb-1">
                    <Box size={16} className="text-purple-500" />
                    <span>Режим Проект</span>
                  </div>
                  <p className="text-xs text-slate-500 dark:text-slate-400">
                    Збери проект з декількох деталей — кожну на своєму принтері.
                  </p>
                </div>
                <button
                  onClick={() => setProjectMode(!projectMode)}
                  className={`px-6 py-2.5 rounded-full text-sm font-semibold transition-all ${
                    projectMode
                      ? 'bg-purple-600 text-white'
                      : 'bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300'
                  }`}
                >
                  {projectMode ? 'Вийти' : 'Включити'}
                </button>
              </div>

              {projectMode && (
                <div className="mt-4 flex gap-3">
                  <button className="flex-1 py-3 bg-blue-600 text-white rounded-2xl font-semibold text-sm hover:bg-blue-700 transition-colors">
                    + Додати деталь
                  </button>
                  <button className="px-4 py-3 bg-slate-100 dark:bg-slate-700 rounded-2xl hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors">
                    <Save size={18} />
                  </button>
                  <button className="px-4 py-3 bg-slate-100 dark:bg-slate-700 rounded-2xl hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors">
                    <FolderOpen size={18} />
                  </button>
                </div>
              )}
            </div>

            {/* Матеріал */}
            <div className="bg-white dark:bg-slate-800 rounded-3xl p-6 shadow-lg">
              <div className="flex items-center justify-between mb-6">
                <div className="flex items-center gap-2 text-sm font-semibold">
                  <span className="text-orange-500">🎨</span>
                  <span>Матеріал</span>
                </div>
                <label className="flex items-center gap-3 cursor-pointer">
                  <span className="text-xs text-slate-500">Багатоколірна печать (AMS)</span>
                  <div
                    onClick={() => setIsAMS(!isAMS)}
                    className={`relative w-12 h-6 rounded-full transition-all ${
                      isAMS ? 'bg-blue-600' : 'bg-slate-300 dark:bg-slate-600'
                    }`}
                  >
                    <div
                      className={`absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full transition-transform ${
                        isAMS ? 'translate-x-6' : 'translate-x-0'
                      }`}
                    />
                  </div>
                </label>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
                    Вага виробу (г)
                  </label>
                  <input
                    type="number"
                    value={weight}
                    onChange={(e) => setWeight(Number(e.target.value))}
                    className="w-full px-4 py-3 bg-slate-50 dark:bg-slate-700 rounded-xl border-2 border-transparent focus:border-blue-400 dark:focus:border-blue-500 outline-none transition-all"
                    placeholder="0"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
                    Ціна пластику (₴/кг)
                  </label>
                  <input
                    type="number"
                    value={plasticPrice}
                    onChange={(e) => setPlasticPrice(Number(e.target.value))}
                    className="w-full px-4 py-3 bg-slate-50 dark:bg-slate-700 rounded-xl border-2 border-transparent focus:border-blue-400 dark:focus:border-blue-500 outline-none transition-all"
                    placeholder="0"
                  />
                </div>
              </div>
            </div>

            {/* AMS секція */}
            {isAMS && (
              <div className="bg-white dark:bg-slate-800 rounded-3xl p-6 shadow-lg animate-in slide-in-from-top-4">
                <div className="flex items-center gap-2 text-sm font-semibold mb-6">
                  <span className="text-pink-500">🎨</span>
                  <span>Мультиколір / AMS</span>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
                      Кількість кольорів
                    </label>
                    <input
                      type="number"
                      value={colorCount}
                      onChange={(e) => setColorCount(Number(e.target.value))}
                      className="w-full px-4 py-3 bg-slate-50 dark:bg-slate-700 rounded-xl border-2 border-transparent focus:border-blue-400 dark:focus:border-blue-500 outline-none transition-all"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
                      Зміни кольору / слоїв
                    </label>
                    <input
                      type="number"
                      value={colorChanges}
                      onChange={(e) => setColorChanges(Number(e.target.value))}
                      className="w-full px-4 py-3 bg-slate-50 dark:bg-slate-700 rounded-xl border-2 border-transparent focus:border-blue-400 dark:focus:border-blue-500 outline-none transition-all"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
                      Злив на 1 зміну (г)
                    </label>
                    <input
                      type="number"
                      step="0.1"
                      value={flushPerChange}
                      onChange={(e) => setFlushPerChange(Number(e.target.value))}
                      className="w-full px-4 py-3 bg-slate-50 dark:bg-slate-700 rounded-xl border-2 border-transparent focus:border-blue-400 dark:focus:border-blue-500 outline-none transition-all"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
                      Вага відходів (г)
                      <span className="ml-1 text-xs opacity-60">автоматично</span>
                    </label>
                    <input
                      type="number"
                      value={wasteWeight}
                      onChange={(e) => setWasteWeight(Number(e.target.value))}
                      className="w-full px-4 py-3 bg-slate-50 dark:bg-slate-700 rounded-xl border-2 border-transparent focus:border-blue-400 dark:focus:border-blue-500 outline-none transition-all"
                    />
                  </div>
                  <div className="md:col-span-2">
                    <label className="block text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
                      Вага prime-башні (г)
                    </label>
                    <input
                      type="number"
                      value={primeWeight}
                      onChange={(e) => setPrimeWeight(Number(e.target.value))}
                      className="w-full px-4 py-3 bg-slate-50 dark:bg-slate-700 rounded-xl border-2 border-transparent focus:border-blue-400 dark:focus:border-blue-500 outline-none transition-all"
                    />
                  </div>
                </div>
              </div>
            )}

            {/* Час + енергія */}
            <div className="bg-white dark:bg-slate-800 rounded-3xl p-6 shadow-lg">
              <div className="flex items-center gap-2 text-sm font-semibold mb-6">
                <Clock size={16} className="text-blue-500" />
                <span>Час + енергія</span>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
                    Час друку (год)
                  </label>
                  <input
                    type="number"
                    step="0.1"
                    value={printTime}
                    onChange={(e) => setPrintTime(Number(e.target.value))}
                    className="w-full px-4 py-3 bg-slate-50 dark:bg-slate-700 rounded-xl border-2 border-transparent focus:border-blue-400 dark:focus:border-blue-500 outline-none transition-all"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
                    Тариф електрики (₴/кВт·год)
                  </label>
                  <input
                    type="number"
                    step="0.01"
                    value={electricityRate}
                    onChange={(e) => setElectricityRate(Number(e.target.value))}
                    className="w-full px-4 py-3 bg-slate-50 dark:bg-slate-700 rounded-xl border-2 border-transparent focus:border-blue-400 dark:focus:border-blue-500 outline-none transition-all"
                  />
                </div>
              </div>
            </div>

            {/* Інше */}
            <div className="bg-white dark:bg-slate-800 rounded-3xl p-6 shadow-lg">
              <div className="flex items-center gap-2 text-sm font-semibold mb-6">
                <span className="text-red-500">📦</span>
                <span>Інше</span>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
                    Упаковка (₴)
                  </label>
                  <input
                    type="number"
                    value={packaging}
                    onChange={(e) => setPackaging(Number(e.target.value))}
                    className="w-full px-4 py-3 bg-slate-50 dark:bg-slate-700 rounded-xl border-2 border-transparent focus:border-blue-400 dark:focus:border-blue-500 outline-none transition-all"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
                    Брак (%)
                  </label>
                  <input
                    type="number"
                    value={defectRate}
                    onChange={(e) => setDefectRate(Number(e.target.value))}
                    className="w-full px-4 py-3 bg-slate-50 dark:bg-slate-700 rounded-xl border-2 border-transparent focus:border-blue-400 dark:focus:border-blue-500 outline-none transition-all"
                  />
                </div>
              </div>
            </div>

            {/* Результат */}
            <div className="bg-gradient-to-br from-blue-50 to-indigo-50 dark:from-slate-800 dark:to-slate-700 rounded-3xl p-8 shadow-xl">
              <div className="text-center mb-6">
                <div className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-2">
                  Собівартість
                </div>
                <div className="text-6xl font-bold text-blue-600 dark:text-blue-400 mb-4">
                  {calculations.totalCost} ₴
                </div>
              </div>

              <div className="space-y-2 text-sm">
                <div className="flex justify-between text-slate-600 dark:text-slate-400">
                  <span>Ресурси</span>
                  <span className="font-semibold">{calculations.resources} ₴</span>
                </div>
                <div className="flex justify-between text-slate-600 dark:text-slate-400">
                  <span>До браку</span>
                  <span className="font-semibold">{calculations.beforeDefect} ₴</span>
                </div>
                <div className="flex justify-between text-slate-600 dark:text-slate-400">
                  <span>Брак ({defectRate}%)</span>
                  <span className="font-semibold">{calculations.defectCost} ₴</span>
                </div>
              </div>
            </div>

            {/* Reset button */}
            <button
              onClick={resetAll}
              className="w-full py-3 text-sm font-semibold text-slate-500 dark:text-slate-400 hover:text-red-600 dark:hover:text-red-400 transition-colors"
            >
              ✕ Скинути всі поля
            </button>
          </div>
        )}

        {activeTab === 'price' && (
          <div className="space-y-6">
            {/* Наценка */}
            <div className="bg-white dark:bg-slate-800 rounded-3xl p-8 shadow-lg">
              <div className="flex items-center gap-2 text-sm font-semibold mb-6">
                <span className="text-yellow-500">💵</span>
                <span>Наценка</span>
              </div>

              <div className="mb-6">
                <label className="block text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
                  Процент наценки (%)
                </label>
                <input
                  type="number"
                  value={markup}
                  onChange={(e) => setMarkup(Number(e.target.value))}
                  className="w-full px-4 py-3 bg-slate-50 dark:bg-slate-700 rounded-xl border-2 border-transparent focus:border-blue-400 dark:focus:border-blue-500 outline-none transition-all text-2xl font-bold text-center"
                />
              </div>

              <input
                type="range"
                min="0"
                max="500"
                value={markup}
                onChange={(e) => setMarkup(Number(e.target.value))}
                className="w-full h-2 bg-slate-200 dark:bg-slate-700 rounded-full appearance-none cursor-pointer accent-blue-600"
              />
            </div>

            {/* Ціна для клієнта */}
            <div className="bg-gradient-to-br from-green-50 to-emerald-50 dark:from-slate-800 dark:to-slate-700 rounded-3xl p-12 shadow-xl">
              <div className="text-center">
                <div className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-4">
                  Ціна для клієнта
                </div>
                <div className="text-7xl font-bold text-green-600 dark:text-green-400 mb-6">
                  {calculations.clientPrice} ₴
                </div>
                <button className="inline-flex items-center gap-2 px-6 py-3 bg-white dark:bg-slate-700 rounded-full text-sm font-semibold shadow-md hover:shadow-lg transition-all">
                  📋 Скопіювати ціну
                </button>
              </div>
            </div>

            {/* Reset button */}
            <button
              onClick={resetAll}
              className="w-full py-3 text-sm font-semibold text-slate-500 dark:text-slate-400 hover:text-red-600 dark:hover:text-red-400 transition-colors"
            >
              ✕ Скинути всі поля
            </button>
          </div>
        )}

        {activeTab === 'file' && (
          <div className="space-y-6">
            {/* File upload */}
            <div className="bg-white dark:bg-slate-800 rounded-3xl p-12 shadow-lg border-4 border-dashed border-slate-300 dark:border-slate-600 text-center hover:border-blue-400 dark:hover:border-blue-500 transition-all cursor-pointer">
              <div className="mb-6">
                <div className="inline-flex items-center justify-center w-20 h-20 bg-blue-100 dark:bg-blue-900/30 rounded-full mb-4">
                  <FileText size={40} className="text-blue-600 dark:text-blue-400" />
                </div>
              </div>
              <h3 className="text-xl font-bold mb-2">Перетягни .3mf / .gcode сюди</h3>
              <p className="text-sm text-slate-500 dark:text-slate-400">
                або клікни, щоб вибрати
              </p>
            </div>
          </div>
        )}

      </div>

      {/* Printer Modal */}
      {showPrinterModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-white dark:bg-slate-800 rounded-3xl p-8 max-w-md w-full shadow-2xl">
            <h3 className="text-xl font-bold mb-6">Новий принтер</h3>

            <div className="space-y-4 mb-6">
              <div>
                <label className="block text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
                  Вибрати зі списку
                </label>
                <select
                  value={newPrinter.preset}
                  onChange={(e) => {
                    const preset = PRINTER_PRESETS.find(p => p.name === e.target.value);
                    setNewPrinter({
                      ...newPrinter,
                      preset: e.target.value,
                      name: e.target.value,
                      power: preset?.power || 100,
                      wear: preset?.wear || 15
                    });
                  }}
                  className="w-full px-4 py-3 bg-slate-50 dark:bg-slate-700 rounded-xl border-2 border-transparent focus:border-blue-400 dark:focus:border-blue-500 outline-none transition-all"
                >
                  <option value="">— вибрати зі списку —</option>
                  {PRINTER_PRESETS.map((preset) => (
                    <option key={preset.name} value={preset.name}>
                      {preset.name} ({preset.power}Вт · {preset.wear}₴/год)
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
                  Назва
                </label>
                <input
                  type="text"
                  value={newPrinter.name}
                  onChange={(e) => setNewPrinter({ ...newPrinter, name: e.target.value })}
                  className="w-full px-4 py-3 bg-slate-50 dark:bg-slate-700 rounded-xl border-2 border-transparent focus:border-blue-400 dark:focus:border-blue-500 outline-none transition-all"
                  placeholder="Мій принтер"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
                  Потужність (Вт)
                </label>
                <input
                  type="number"
                  value={newPrinter.power}
                  onChange={(e) => setNewPrinter({ ...newPrinter, power: Number(e.target.value) })}
                  className="w-full px-4 py-3 bg-slate-50 dark:bg-slate-700 rounded-xl border-2 border-transparent focus:border-blue-400 dark:focus:border-blue-500 outline-none transition-all"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
                  Знос за годину (₴)
                </label>
                <input
                  type="number"
                  value={newPrinter.wear}
                  onChange={(e) => setNewPrinter({ ...newPrinter, wear: Number(e.target.value) })}
                  className="w-full px-4 py-3 bg-slate-50 dark:bg-slate-700 rounded-xl border-2 border-transparent focus:border-blue-400 dark:focus:border-blue-500 outline-none transition-all"
                />
              </div>
            </div>

            <div className="flex gap-3">
              <button
                onClick={() => setShowPrinterModal(false)}
                className="flex-1 py-3 bg-slate-100 dark:bg-slate-700 rounded-xl font-semibold hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors"
              >
                Відміна
              </button>
              <button
                onClick={addPrinter}
                className="flex-1 py-3 bg-blue-600 text-white rounded-xl font-semibold hover:bg-blue-700 transition-colors flex items-center justify-center gap-2"
              >
                <Save size={18} />
                Зберегти
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
