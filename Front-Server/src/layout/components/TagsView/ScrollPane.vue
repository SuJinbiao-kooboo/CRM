<template>
  <div ref="scrollContainer" class="scroll-container" @wheel.prevent="handleScroll">
    <div ref="scrollWrapper" class="scroll-wrapper" :style="{transform: `translateX(${left}px)`}">
      <slot />
    </div>
  </div>
</template>

<script>
const tagAndTagSpacing = 4 // tagAndTagSpacing

export default {
  name: 'ScrollPane',
  data() {
    return {
      left: 0
    }
  },
  computed: {
    scrollWrapper() {
      return this.$refs.scrollWrapper
    },
    scrollContainer() {
      return this.$refs.scrollContainer
    }
  },
  mounted() {
    this.scrollContainer.addEventListener('scroll', this.emitScroll, true)
  },
  beforeDestroy() {
    this.scrollContainer.removeEventListener('scroll', this.emitScroll)
  },
  methods: {
    handleScroll(e) {
      const eventDelta = e.wheelDelta || -e.deltaY * 40
      const $scrollWrapper = this.scrollWrapper
      const $scrollContainer = this.scrollContainer
      const maxOffset = $scrollContainer.offsetWidth - $scrollWrapper.offsetWidth
      if (eventDelta > 0) {
        this.left = Math.min(0, this.left + eventDelta)
      } else {
        if (maxOffset < 0) {
          this.left = Math.max(maxOffset, this.left + eventDelta)
        } else {
          this.left = 0
        }
      }
    },
    emitScroll() {
      this.$emit('scroll')
    },
    moveToTarget(currentTag) {
      const $container = this.scrollContainer
      const $containerWidth = $container.offsetWidth
      const $scrollWrapper = this.scrollWrapper
      const $scrollWrapperWidth = $scrollWrapper.offsetWidth

      let firstTag = null
      let lastTag = null

      // find first tag and last tag
      if ($scrollWrapper.children.length > 0) {
        firstTag = $scrollWrapper.children[0]
        lastTag = $scrollWrapper.children[$scrollWrapper.children.length - 1]
      }

      if (firstTag === currentTag) {
        this.left = 0
      } else if (lastTag === currentTag) {
        this.left = -(($scrollWrapperWidth - $containerWidth) + tagAndTagSpacing)
      } else {
        // find preTag and nextTag
        const currentIndex = [...$scrollWrapper.children].findIndex(item => item === currentTag)
        const prevTag = $scrollWrapper.children[currentIndex - 1]
        const nextTag = $scrollWrapper.children[currentIndex + 1]

        // the tag's offsetLeft after of nextTag
        const afterNextTagOffsetLeft = nextTag.offsetLeft + nextTag.offsetWidth + tagAndTagSpacing

        // the tag's offsetLeft before of prevTag
        const beforePrevTagOffsetLeft = prevTag.offsetLeft - tagAndTagSpacing

        if (afterNextTagOffsetLeft > $containerWidth + Math.abs(this.left)) {
          this.left = -(afterNextTagOffsetLeft - $containerWidth)
        } else if (beforePrevTagOffsetLeft < Math.abs(this.left)) {
          this.left = -beforePrevTagOffsetLeft
        }
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.scroll-container {
  white-space: nowrap;
  position: relative;
  overflow: hidden;
  width: 100%;
  .scroll-wrapper {
    position: absolute;
    transition: transform .3s ease-in-out;
  }
}
</style>