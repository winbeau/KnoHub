<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { Upload, ZoomIn, ZoomOut, Move, RefreshCcw, FileCode, AlertCircle, Cpu } from 'lucide-vue-next';

/**
 * 类型定义
 */
interface Point {
  x: number;
  y: number;
}

interface Wire {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
}

interface Component {
  id: string;
  name: string;
  lib: string;
  x: number;
  y: number;
  facing: 'east' | 'west' | 'north' | 'south';
  label: string;
  width?: number; // 部分组件可能有宽度属性
}

interface CircuitData {
  wires: Wire[];
  components: Component[];
  name: string;
}

// 状态管理
const fileInput = ref<HTMLInputElement | null>(null);
const rawXml = ref<string>('');
const circuitName = ref<string>('未命名电路');
const parsedData = ref<CircuitData | null>(null);
const errorMsg = ref<string>('');
const isLoading = ref<boolean>(false);

// 视图控制
const scale = ref<number>(1);
const pan = ref<Point>({ x: 0, y: 0 });
const isDragging = ref<boolean>(false);
const dragStart = ref<Point>({ x: 0, y: 0 });

// Logisim 的标准网格大小通常是 10 或 20 像素，这里为了显示清晰，放大处理
const GRID_SIZE = 20;

/**
 * 文件处理逻辑
 */
const triggerUpload = () => {
  fileInput.value?.click();
};

const handleFileUpload = (event: Event) => {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  
  if (!file) return;

  // 重置状态
  errorMsg.value = '';
  parsedData.value = null;
  scale.value = 1;
  pan.value = { x: 0, y: 0 };
  isLoading.value = true;

  const reader = new FileReader();
  reader.onload = (e) => {
    try {
      const content = e.target?.result as string;
      rawXml.value = content;
      parseLogisimFile(content);
    } catch (err) {
      errorMsg.value = '文件解析失败，请确保这是一个有效的 .circ 文件';
      console.error(err);
    } finally {
      isLoading.value = false;
    }
  };
  reader.readAsText(file);
};

/**
 * XML 解析核心逻辑
 */
const parseLogisimFile = (xmlString: string) => {
  const parser = new DOMParser();
  const xmlDoc = parser.parseFromString(xmlString, "text/xml");

  const project = xmlDoc.getElementsByTagName("project")[0];
  if (!project) {
    throw new Error("无效的 Logisim 文件：找不到 <project> 标签");
  }

  // 获取主电路 (默认取第一个 circuit)
  const circuit = xmlDoc.getElementsByTagName("circuit")[0];
  if (!circuit) {
    throw new Error("找不到电路定义");
  }

  // 获取电路名称
  const nameAttr = circuit.getAttribute("name");
  circuitName.value = nameAttr || "主电路";

  const wires: Wire[] = [];
  const components: Component[] = [];

  // 1. 解析连线 (Wires)
  const wireTags = circuit.getElementsByTagName("wire");
  for (let i = 0; i < wireTags.length; i++) {
    const w = wireTags[i];
    const from = w.getAttribute("from");
    const to = w.getAttribute("to");
    if (from && to) {
      const p1 = parseCoords(from);
      const p2 = parseCoords(to);
      wires.push({ x1: p1.x, y1: p1.y, x2: p2.x, y2: p2.y });
    }
  }

  // 2. 解析组件 (Components)
  const compTags = circuit.getElementsByTagName("comp");
  for (let i = 0; i < compTags.length; i++) {
    const c = compTags[i];
    const loc = c.getAttribute("loc");
    const name = c.getAttribute("name");
    const lib = c.getAttribute("lib") || ""; // lib 索引

    if (loc && name) {
      const p = parseCoords(loc);
      
      // 解析属性 (a标签)
      let facing: Component['facing'] = 'east'; // 默认朝东
      let label = '';
      
      const attrs = c.getElementsByTagName("a");
      for (let j = 0; j < attrs.length; j++) {
        const attrName = attrs[j].getAttribute("name");
        const attrVal = attrs[j].getAttribute("val");
        if (attrName === 'facing' && attrVal) facing = attrVal.toLowerCase() as any;
        if (attrName === 'label' && attrVal) label = attrVal;
        if (attrName === 'Text' && attrVal) label = attrVal; // Text组件的内容
      }

      components.push({
        id: `comp-${i}`,
        name,
        lib,
        x: p.x,
        y: p.y,
        facing,
        label
      });
    }
  }

  parsedData.value = {
    name: circuitName.value,
    wires,
    components
  };
  
  // 自动居中视图
  centerView(wires, components);
};

// 解析 "(x,y)" 字符串
const parseCoords = (str: string): Point => {
  const match = str.match(/\((-?\d+),(-?\d+)\)/);
  if (match) {
    return { x: parseInt(match[1], 10), y: parseInt(match[2], 10) };
  }
  return { x: 0, y: 0 };
};

/**
 * 渲染辅助函数
 */

// 计算视图边界并居中
const centerView = (wires: Wire[], components: Component[]) => {
  if (wires.length === 0 && components.length === 0) return;

  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;

  const updateBounds = (x: number, y: number) => {
    if (x < minX) minX = x;
    if (x > maxX) maxX = x;
    if (y < minY) minY = y;
    if (y > maxY) maxY = y;
  };

  wires.forEach(w => {
    updateBounds(w.x1, w.y1);
    updateBounds(w.x2, w.y2);
  });

  components.forEach(c => {
    updateBounds(c.x, c.y);
  });

  // 增加一些 padding
  const padding = 100;
  const width = maxX - minX;
  const height = maxY - minY;
  
  // 简单的居中计算 (假设画布容器大概 800x600)
  pan.value = {
    x: -minX + 100,
    y: -minY + 100
  };
};

// 获取组件旋转角度
const getRotation = (facing: string) => {
  switch (facing) {
    case 'north': return -90;
    case 'south': return 90;
    case 'west': return 180;
    default: return 0;
  }
};

// 交互逻辑：拖拽
const startDrag = (e: MouseEvent) => {
  isDragging.value = true;
  dragStart.value = { x: e.clientX - pan.value.x, y: e.clientY - pan.value.y };
};

const onDrag = (e: MouseEvent) => {
  if (!isDragging.value) return;
  pan.value = {
    x: e.clientX - dragStart.value.x,
    y: e.clientY - dragStart.value.y
  };
};

const stopDrag = () => {
  isDragging.value = false;
};

// 缩放
const zoom = (delta: number) => {
  const newScale = scale.value + delta;
  if (newScale > 0.1 && newScale < 5) {
    scale.value = newScale;
  }
};

</script>

<template>
  <div class="flex flex-col h-screen bg-slate-50 text-slate-900 font-sans overflow-hidden">
    <!-- 顶部导航栏 -->
    <header class="flex items-center justify-between px-6 py-3 bg-white border-b border-slate-200 shadow-sm z-10">
      <div class="flex items-center gap-2">
        <div class="bg-blue-600 p-1.5 rounded-lg">
          <Cpu class="w-5 h-5 text-white" />
        </div>
        <h1 class="font-bold text-lg text-slate-800">Logisim 网页预览器 <span class="text-xs font-normal text-slate-500 ml-1">Beta</span></h1>
      </div>
      
      <div class="flex items-center gap-4">
        <div v-if="parsedData" class="text-sm font-medium px-3 py-1 bg-slate-100 rounded-full text-slate-600">
           正在查看: {{ parsedData.name }}
        </div>
        
        <input 
          type="file" 
          ref="fileInput" 
          accept=".circ" 
          class="hidden" 
          @change="handleFileUpload"
        />
        
        <button 
          @click="triggerUpload"
          class="flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-md transition-colors shadow-sm"
        >
          <Upload class="w-4 h-4" />
          {{ parsedData ? '打开新文件' : '上传 .circ 文件' }}
        </button>
      </div>
    </header>

    <!-- 主体区域 -->
    <main class="flex-1 relative overflow-hidden bg-slate-100 cursor-grab active:cursor-grabbing" 
      @mousedown="startDrag" 
      @mousemove="onDrag" 
      @mouseup="stopDrag" 
      @mouseleave="stopDrag"
    >
      
      <!-- 背景网格图案 -->
      <div 
        class="absolute inset-0 pointer-events-none opacity-10"
        :style="{
          backgroundImage: `radial-gradient(#475569 1px, transparent 1px)`,
          backgroundSize: `${20 * scale}px ${20 * scale}px`,
          backgroundPosition: `${pan.x}px ${pan.y}px`
        }"
      ></div>

      <!-- 空状态 -->
      <div v-if="!parsedData && !isLoading" class="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
        <div class="bg-white p-8 rounded-xl shadow-xl text-center max-w-md border border-slate-200">
          <div class="w-16 h-16 bg-blue-50 text-blue-500 rounded-full flex items-center justify-center mx-auto mb-4">
            <FileCode class="w-8 h-8" />
          </div>
          <h2 class="text-xl font-bold text-slate-800 mb-2">开始预览电路</h2>
          <p class="text-slate-500 mb-6">点击右上角上传 Logisim (.circ) 文件。目前支持 Logisim 2.7.x 版本的基础组件解析。</p>
          <div class="text-xs text-slate-400 bg-slate-50 p-3 rounded border border-slate-100 text-left">
            <p class="font-bold mb-1">支持的组件：</p>
            Pin, Probe, AND, OR, NOT, NAND, NOR, XOR, LED, Text
          </div>
        </div>
      </div>

      <!-- 加载状态 -->
      <div v-if="isLoading" class="absolute inset-0 flex items-center justify-center bg-white/50 backdrop-blur-sm z-20">
        <RefreshCcw class="w-8 h-8 text-blue-600 animate-spin" />
      </div>

      <!-- 错误提示 -->
      <div v-if="errorMsg" class="absolute top-6 left-1/2 -translate-x-1/2 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg shadow-lg flex items-center gap-2 z-30">
        <AlertCircle class="w-5 h-5" />
        <span>{{ errorMsg }}</span>
      </div>

      <!-- 画布控制器 -->
      <div v-if="parsedData" class="absolute bottom-6 right-6 flex flex-col gap-2 bg-white p-2 rounded-lg shadow-lg border border-slate-200 z-20">
        <button @click="zoom(0.1)" class="p-2 hover:bg-slate-100 rounded text-slate-600" title="放大">
          <ZoomIn class="w-5 h-5" />
        </button>
        <div class="text-center text-xs font-mono text-slate-400 select-none">{{ Math.round(scale * 100) }}%</div>
        <button @click="zoom(-0.1)" class="p-2 hover:bg-slate-100 rounded text-slate-600" title="缩小">
          <ZoomOut class="w-5 h-5" />
        </button>
        <button @click="centerView(parsedData.wires, parsedData.components)" class="p-2 hover:bg-slate-100 rounded text-slate-600 border-t border-slate-100 mt-1" title="复位视图">
          <Move class="w-5 h-5" />
        </button>
      </div>

      <!-- 电路渲染层 SVG -->
      <div v-if="parsedData" class="w-full h-full origin-top-left" 
        :style="{ transform: `translate(${pan.x}px, ${pan.y}px) scale(${scale})` }">
        <svg class="overflow-visible w-full h-full">
          
          <!-- 1. 连线层 -->
          <g class="wires">
            <line 
              v-for="(wire, idx) in parsedData.wires" 
              :key="`w-${idx}`"
              :x1="wire.x1" 
              :y1="wire.y1" 
              :x2="wire.x2" 
              :y2="wire.y2" 
              stroke="#334155" 
              stroke-width="3" 
              stroke-linecap="round"
            />
          </g>

          <!-- 2. 组件层 -->
          <g class="components">
            <g 
              v-for="comp in parsedData.components" 
              :key="comp.id"
              :transform="`translate(${comp.x}, ${comp.y}) rotate(${getRotation(comp.facing)})`"
            >
              
              <!-- 调试：显示组件原点 -->
              <!-- <circle cx="0" cy="0" r="2" fill="red" /> -->

              <!-- Pin / Input -->
              <g v-if="comp.name === 'Pin'">
                <rect x="-10" y="-10" width="20" height="20" fill="#fff" stroke="#1e293b" stroke-width="2" />
                <text v-if="comp.label" x="0" y="-15" text-anchor="middle" class="text-xs font-sans fill-slate-700 select-none" style="transform: rotate(0)">{{ comp.label }}</text>
                <!-- Pin Output indicator (circle vs square depends on output attr, simplified here) -->
                <circle cx="0" cy="0" r="4" fill="#cbd5e1" />
              </g>

              <!-- Probe / Output -->
              <g v-else-if="comp.name === 'Probe'">
                <circle cx="0" cy="0" r="15" fill="#fff" stroke="#1e293b" stroke-width="2" />
                <text x="0" y="5" text-anchor="middle" class="text-xs font-mono">?</text>
              </g>

              <!-- AND Gate -->
              <g v-else-if="comp.name === 'AND Gate'">
                 <!-- Logisim Gates usually origin at output, inputs to the west (-x) -->
                 <!-- Drawing a standard AND shape centered somewhat -->
                 <path d="M -40 -20 L -20 -20 Q 0 -20 0 0 Q 0 20 -20 20 L -40 20 Z" fill="#fff" stroke="#1e293b" stroke-width="2" />
                 <!-- Input stubs (simplified, assuming 2 inputs) -->
                 <line x1="-40" y1="-10" x2="-50" y2="-10" stroke="#1e293b" stroke-width="2" />
                 <line x1="-40" y1="10" x2="-50" y2="10" stroke="#1e293b" stroke-width="2" />
              </g>

              <!-- OR Gate -->
              <g v-else-if="comp.name === 'OR Gate'">
                 <path d="M -45 -20 Q -25 -20 0 0 Q -25 20 -45 20 Q -35 0 -45 -20 Z" fill="#fff" stroke="#1e293b" stroke-width="2" />
                 <line x1="-42" y1="-10" x2="-50" y2="-10" stroke="#1e293b" stroke-width="2" />
                 <line x1="-42" y1="10" x2="-50" y2="10" stroke="#1e293b" stroke-width="2" />
              </g>

              <!-- NOT Gate -->
              <g v-else-if="comp.name === 'NOT Gate'">
                 <path d="M -30 -10 L 0 0 L -30 10 Z" fill="#fff" stroke="#1e293b" stroke-width="2" />
                 <circle cx="5" cy="0" r="3" fill="#fff" stroke="#1e293b" stroke-width="2" />
              </g>

              <!-- NAND Gate -->
               <g v-else-if="comp.name === 'NAND Gate'">
                 <path d="M -40 -20 L -20 -20 Q 0 -20 0 0 Q 0 20 -20 20 L -40 20 Z" fill="#fff" stroke="#1e293b" stroke-width="2" />
                 <circle cx="5" cy="0" r="4" fill="#fff" stroke="#1e293b" stroke-width="2" />
                 <line x1="-40" y1="-10" x2="-50" y2="-10" stroke="#1e293b" stroke-width="2" />
                 <line x1="-40" y1="10" x2="-50" y2="10" stroke="#1e293b" stroke-width="2" />
              </g>

               <!-- XOR Gate -->
               <g v-else-if="comp.name === 'XOR Gate'">
                 <path d="M -40 -20 Q -20 -20 0 0 Q -20 20 -40 20 Q -30 0 -40 -20 Z" fill="#fff" stroke="#1e293b" stroke-width="2" />
                 <path d="M -48 -20 Q -38 0 -48 20" fill="none" stroke="#1e293b" stroke-width="2" />
                 <line x1="-45" y1="-10" x2="-55" y2="-10" stroke="#1e293b" stroke-width="2" />
                 <line x1="-45" y1="10" x2="-55" y2="10" stroke="#1e293b" stroke-width="2" />
              </g>

              <!-- Text / Label -->
              <g v-else-if="comp.name === 'Text'">
                 <text x="0" y="0" text-anchor="middle" dominant-baseline="middle" class="text-sm font-sans fill-slate-500 select-none" style="font-family: monospace">{{ comp.label || 'Text' }}</text>
              </g>

              <!-- LED -->
              <g v-else-if="comp.name === 'LED'">
                 <circle cx="0" cy="0" r="10" fill="#ef4444" stroke="#991b1b" stroke-width="2" />
              </g>
              
              <!-- Generic Fallback -->
              <g v-else>
                 <rect x="-15" y="-15" width="30" height="30" fill="#f1f5f9" stroke="#94a3b8" stroke-width="1" stroke-dasharray="2 2" />
                 <text x="0" y="0" text-anchor="middle" class="text-[8px] fill-slate-500">{{ comp.name }}</text>
              </g>

            </g>
          </g>

        </svg>
      </div>
    </main>
  </div>
</template>

<style scoped>
/* 可以在这里添加一些针对 SVG 的特定样式 */
</style>
