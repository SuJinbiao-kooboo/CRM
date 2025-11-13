<template>
  <div>
    <el-tag
      v-for="(item, index) in options"
      :key="index"
      :type="item.listClass == 'primary' ? '' : item.listClass"
      :class="item.cssClass"
      v-show="values.includes(item.dictValue)"
    >
      {{ item.dictLabel }}
    </el-tag>

    <template v-if="unmatch && showValue">
      {{ unmatchArray | handleArray }}
    </template>
  </div>
</template>

<script>
export default {
  name: 'DictTag',
  props: {
    options: {
      type: Array,
      default: null
    },
    value: [Number, String, Array],
    showValue: {
      type: Boolean,
      default: true
    }
  },
  computed: {
    values() {
      if (this.value !== null && typeof this.value !== 'undefined') {
        return Array.isArray(this.value) ? this.value : [String(this.value)]
      } else {
        return []
      }
    },
    unmatchArray() {
      return this.values.filter((item) => {
        const matchedItem = this.options.find((option) => option.dictValue == item)
        return !matchedItem
      })
    },
    unmatch() {
      return this.unmatchArray && this.unmatchArray.length > 0
    }
  },
  filters: {
    handleArray(array) {
      if (array && array.length > 0) {
        return array.join(',')
      } else {
        return ''
      }
    }
  }
}
</script>