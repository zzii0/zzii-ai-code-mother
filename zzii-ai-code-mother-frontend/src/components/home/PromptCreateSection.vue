<template>
  <div class="prompt-create-section">
    <div class="hero-section">
      <h1 class="hero-title">{{ SITE_NAME }}</h1>
      <p class="hero-description">{{ SITE_DESCRIPTION }}</p>
    </div>

    <div class="input-section">
      <a-textarea
        :value="modelValue"
        placeholder="帮我创建个人博客网站"
        :rows="4"
        :maxlength="1000"
        class="prompt-input"
        @update:value="$emit('update:modelValue', $event)"
      />
      <div class="input-actions">
        <a-button type="primary" size="large" :loading="creating" @click="$emit('create')">
          <template #icon>
            <RocketOutlined />
          </template>
          创建应用
        </a-button>
      </div>
    </div>

    <div class="quick-actions">
      <a-button
        v-for="item in quickPrompts"
        :key="item.label"
        type="default"
        @click="$emit('update:modelValue', item.prompt)"
      >
        {{ item.label }}
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { RocketOutlined } from '@ant-design/icons-vue'
import { SITE_NAME, SITE_DESCRIPTION } from '@/config/site'

defineProps<{
  modelValue: string
  creating: boolean
}>()

defineEmits<{
  'update:modelValue': [value: string]
  create: []
}>()

const quickPrompts = [
  {
    label: '个人博客网站',
    prompt:
      '创建一个现代化的个人博客网站，包含文章列表、详情页、分类标签、搜索功能、评论系统和个人简介页面。采用简洁的设计风格，支持响应式布局，文章支持Markdown格式，首页展示最新文章和热门推荐。',
  },
  {
    label: '企业官网',
    prompt:
      '设计一个专业的企业官网，包含公司介绍、产品服务展示、新闻资讯、联系我们等页面。采用商务风格的设计，包含轮播图、产品展示卡片、团队介绍、客户案例展示，支持多语言切换和在线客服功能。',
  },
  {
    label: '在线商城',
    prompt:
      '构建一个功能完整的在线商城，包含商品展示、购物车、用户注册登录、订单管理、支付结算等功能。设计现代化的商品卡片布局，支持商品搜索筛选、用户评价、优惠券系统和会员积分功能。',
  },
  {
    label: '作品展示网站',
    prompt:
      '制作一个精美的作品展示网站，适合设计师、摄影师、艺术家等创作者。包含作品画廊、项目详情页、个人简历、联系方式等模块。采用瀑布流或网格布局展示作品，支持图片放大预览和作品分类筛选。',
  },
]
</script>

<style scoped>
.hero-section {
  text-align: center;
  padding: 80px 0 60px;
  margin-bottom: 28px;
  color: #1e293b;
}

.hero-title {
  font-size: 56px;
  font-weight: 700;
  margin: 0 0 20px;
  line-height: 1.2;
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 50%, #10b981 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: -1px;
}

.hero-description {
  font-size: 20px;
  margin: 0;
  opacity: 0.8;
  color: #64748b;
}

.input-section {
  position: relative;
  margin: 0 auto 24px;
  max-width: 800px;
}

.prompt-input {
  border-radius: 16px;
  border: none;
  font-size: 16px;
  padding: 20px 60px 20px 20px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
}

.input-actions {
  position: absolute;
  bottom: 12px;
  right: 12px;
}

.input-actions :deep(.ant-btn) {
  border-radius: 12px;
  padding: 0 20px;
  height: 40px;
}

.quick-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-bottom: 60px;
  flex-wrap: wrap;
}

.quick-actions :deep(.ant-btn) {
  border-radius: 25px;
  padding: 8px 20px;
  height: auto;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(59, 130, 246, 0.2);
  color: #475569;
}

@media (max-width: 768px) {
  .hero-title {
    font-size: 32px;
  }

  .hero-description {
    font-size: 16px;
  }
}
</style>
