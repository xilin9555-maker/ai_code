<script setup lang="ts">
import { ArrowUpOutlined } from '@ant-design/icons-vue'
defineProps<{ title: string; subtitle: string; category: string; previewStyle: string }>()
defineEmits<{ select: [] }>()
</script>
<template>
  <button class="inspiration-card" @click="$emit('select')" :aria-label="`使用灵感：${title}`">
    <div class="preview-art" :class="previewStyle" aria-hidden="true">
      <div v-if="previewStyle === 'portfolio'" class="mini-portfolio">
        <div class="mini-nav"><b>Studio. N</b><span>WORK &nbsp; ABOUT &nbsp; ↗</span></div>
        <div class="portfolio-body">
          <div>
            <span class="tiny-label">INDEPENDENT DESIGNER</span
            ><strong>Less, but<br /><i>better.</i></strong
            ><span class="mini-line" />
          </div>
          <div class="sculpture">
            <div />
            <div />
            <div />
          </div>
        </div>
        <div class="mini-bottom">SELECTED WORKS — 2026 <span>↓</span></div>
      </div>
      <div v-else-if="previewStyle === 'coffee'" class="mini-coffee">
        <div class="mini-nav"><b>慢半拍</b><span>OUR STORY &nbsp; MENU</span></div>
        <div class="coffee-body">
          <span>A LITTLE PAUSE.</span><strong>日子慢一点，<br />咖啡香一点。</strong>
          <div class="coffee-cup"><div /></div>
        </div>
        <div class="mini-bottom">COFFEE & GOOD DAYS <span>EST. 2026</span></div>
      </div>
      <div v-else-if="previewStyle === 'planner'" class="mini-planner">
        <div class="mini-nav"><b>daylight ☀</b><span>YOUR LITTLE SPACE</span></div>
        <div class="planner-body">
          <div>
            <span class="tiny-label">MONDAY, SEPTEMBER 07</span><strong>A fresh start.</strong>
            <div class="mini-task"><span>✓</span> 留一点时间给自己</div>
            <div class="mini-task"><span>○</span> 完成今天的小目标</div>
            <div class="mini-task"><span>○</span> 出门散步 20 分钟</div>
          </div>
          <div class="mini-progress"><span>1/3</span><small>慢慢来，也很快</small></div>
        </div>
      </div>
      <div v-else class="mini-shop">
        <div class="mini-nav"><b>NORD GOODS</b><span>NEW &nbsp; SHOP &nbsp; CART</span></div>
        <div class="shop-copy">
          <span class="tiny-label">SELECTED FOR DAILY LIFE</span>
          <strong>简单生活，<br />认真选择。</strong>
        </div>
        <div class="shop-products"><span>◒</span><span>▰</span><span>◉</span></div>
      </div>
    </div>
    <div class="inspiration-info">
      <div>
        <span class="card-category">{{ category }}</span>
        <h3>{{ title }}</h3>
        <p>{{ subtitle }}</p>
      </div>
      <span class="card-arrow"><ArrowUpOutlined class="diagonal-arrow" /></span>
    </div>
  </button>
</template>
