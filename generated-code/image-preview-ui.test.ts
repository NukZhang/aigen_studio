import { describe, expect, it } from 'vitest'
import * as fs from 'fs'
import * as path from 'path'

interface TestResult {
  passed: boolean
  message: string
  details?: string
}

interface ImageTestResult extends TestResult {
  imageUrl?: string
  gridClass?: string
}

describe('图片预览UI组件测试', () => {
  const htmlPath = path.resolve(__dirname, 'index.html')
  let htmlContent: string
  let results: {
    imagePresence: ImageTestResult[]
    gridLayout: TestResult[]
    avatarImages: TestResult[]
    imageUrls: TestResult[]
  }

  beforeAll(() => {
    htmlContent = fs.readFileSync(htmlPath, 'utf-8')

    results = {
      imagePresence: [],
      gridLayout: [],
      avatarImages: [],
      imageUrls: []
    }
  })

  describe('图片存在性测试', () => {
    it('应该包含用户头像图片', () => {
      const avatarRegex = /<img[^>]+class="[^"]*(?:avatar|user-avatar|comment-avatar)[^"]*"[^>]*>/gi
      const matches = htmlContent.match(avatarRegex)

      if (!matches || matches.length === 0) {
        results.imagePresence.push({
          passed: false,
          message: '缺少用户头像图片',
          details: '未找到.avatar, .user-avatar或.comment-avatar类名的img元素'
        })
        return
      }

      matches.forEach((match) => {
        const srcMatch = match.match(/src="([^"]+)"/)
        if (srcMatch && srcMatch[1]) {
          results.imagePresence.push({
            passed: true,
            message: `找到用户头像: ${srcMatch[1]}`,
            imageUrl: srcMatch[1]
          })
        }
      })

      expect(matches && matches.length).toBeGreaterThan(0)
    })

    it('应该包含动态配图', () => {
      const postImageRegex = /<img[^>]+class="[^"]*post-image[^"]*"[^>]*>/gi
      const matches = htmlContent.match(postImageRegex)

      if (!matches || matches.length === 0) {
        results.imagePresence.push({
          passed: false,
          message: '缺少动态配图',
          details: '未找到.post-image类名的img元素'
        })
        return
      }

      matches.forEach((match) => {
        const srcMatch = match.match(/src="([^"]+)"/)
        if (srcMatch && srcMatch[1]) {
          results.imagePresence.push({
            passed: true,
            message: `找到动态配图: ${srcMatch[1]}`,
            imageUrl: srcMatch[1]
          })
        }
      })

      expect(matches && matches.length).toBeGreaterThan(0)
    })
  })

  describe('图片网格布局测试', () => {
    it('应该使用CSS Grid实现图片布局', () => {
      const containerRegex = /<div[^>]+class="[^"]*post-images[^"]*(?:single|double|multiple)?[^"]*"[^>]*>/gi
      const matches = htmlContent.match(containerRegex)

      if (!matches || matches.length === 0) {
        results.gridLayout.push({
          passed: false,
          message: '缺少图片容器',
          details: '未找到.post-images类名的容器元素'
        })
        return
      }

      matches.forEach((match) => {
        const hasSingle = /class="[^"]*single[^"]*"/.test(match)
        const hasDouble = /class="[^"]*double[^"]*"/.test(match)
        const hasMultiple = /class="[^"]*multiple[^"]*"/.test(match)

        if (hasSingle) {
          results.gridLayout.push({
            passed: true,
            message: '图片容器使用单图布局: .single',
            gridClass: 'single'
          })
        } else if (hasDouble) {
          results.gridLayout.push({
            passed: true,
            message: '图片容器使用双图布局: .double',
            gridClass: 'double'
          })
        } else if (hasMultiple) {
          results.gridLayout.push({
            passed: true,
            message: '图片容器使用多图布局: .multiple',
            gridClass: 'multiple'
          })
        } else {
          results.gridLayout.push({
            passed: false,
            message: '图片容器缺少布局类',
            details: `找到.post-images但缺少single/double/multiple类`
          })
        }
      })

      expect(matches && matches.length).toBeGreaterThan(0)
    })

    it('单图布局应使用 grid-template-columns: 1fr', () => {
      const singleStyleRegex = /\.post-images\.single\s*\{[^}]*grid-template-columns:\s*1fr[^}]*\}/gi
      const matches = htmlContent.match(singleStyleRegex)

      if (matches && matches.length > 0) {
        results.gridLayout.push({
          passed: true,
          message: '单图布局使用正确的CSS: grid-template-columns: 1fr'
        })
      } else {
        results.gridLayout.push({
          passed: false,
          message: '缺少单图布局样式',
          details: '未找到.post-images.single { grid-template-columns: 1fr }样式定义'
        })
      }

      expect(matches && matches.length).toBeGreaterThan(0)
    })

    it('多图布局应使用 grid-template-columns: repeat(3, 1fr)', () => {
      const multipleStyleRegex = /\.post-images\.multiple\s*\{[^}]*grid-template-columns:\s*repeat\(\s*3\s*,\s*1fr\)[^}]*\}/gi
      const matches = htmlContent.match(multipleStyleRegex)

      if (matches && matches.length > 0) {
        results.gridLayout.push({
          passed: true,
          message: '多图布局使用正确的CSS: grid-template-columns: repeat(3, 1fr)'
        })
      } else {
        results.gridLayout.push({
          passed: false,
          message: '缺少多图布局样式',
          details: '未找到.post-images.multiple { grid-template-columns: repeat(3, 1fr) }样式定义'
        })
      }

      expect(matches && matches.length).toBeGreaterThan(0)
    })
  })

  describe('图片URL验证测试', () => {
    it('头像应使用DiceBear API', () => {
      const avatarRegex = /src="([^"]*dicebear[^"]*)"/gi
      const matches = [...htmlContent.matchAll(avatarRegex)]

      if (matches.length > 0) {
        matches.forEach((match) => {
          results.avatarImages.push({
            passed: true,
            message: `头像使用DiceBear API: ${match[1]}`
          })
        })
      } else {
        results.avatarImages.push({
          passed: false,
          message: '头像未使用DiceBear API',
          details: '建议使用https://api.dicebear.com/7.x/avataaars/svg生成头像'
        })
      }

      expect(matches.length).toBeGreaterThan(0)
    })

    it('动态配图应使用有效URL而非占位符', () => {
      const placeholderPatterns = [
        /placeholder/i,
        /via\.placeholder\.com/i,
        /dummyimage/i,
        /placehold\.it/i,
        /gray\.svg/i
      ]

      const postImageRegex = /src="([^"]+post-image[^"]*)"/gi
      const matches = [...htmlContent.matchAll(postImageRegex)]

      let validCount = 0
      let invalidCount = 0

      matches.forEach((match) => {
        const url = match[1]
        const isPlaceholder = placeholderPatterns.some(pattern => pattern.test(url))

        if (!isPlaceholder && (url.startsWith('http://') || url.startsWith('https://'))) {
          validCount++
          results.imageUrls.push({
            passed: true,
            message: `动态配图使用有效URL: ${url.substring(0, 80)}...`
          })
        } else if (isPlaceholder) {
          invalidCount++
          results.imageUrls.push({
            passed: false,
            message: `使用占位符图片: ${url}`
          })
        }
      })

      if (validCount === 0 && invalidCount === 0) {
        results.imageUrls.push({
          passed: false,
          message: '未找到动态配图',
          details: '需要使用.post-image类名包含真实图片URL的img元素'
        })
      }

      expect(validCount).toBeGreaterThan(0)
    })

    it('图片URL应包含尺寸参数', () => {
      const sizedImageRegex = /src="([^"]*(?:w=|width=)[^"]*)"/gi
      const matches = [...htmlContent.matchAll(sizedImageRegex)]

      if (matches.length > 0) {
        results.imageUrls.push({
          passed: true,
          message: `${matches.length}张图片包含尺寸参数`
        })
      } else {
        results.imageUrls.push({
          passed: false,
          message: '图片缺少尺寸参数',
          details: '建议为图片URL添加w=400等尺寸参数以优化加载'
        })
      }

      expect(matches.length).toBeGreaterThan(0)
    })
  })

  describe('图片组件完整性测试', () => {
    it('应该包含至少3条动态卡片', () => {
      const postCardRegex = /<article[^>]+class="[^"]*post-card[^"]*"[^>]*>/gi
      const matches = htmlContent.match(postCardRegex)

      const count = matches ? matches.length : 0

      results.gridLayout.push({
        passed: count >= 3,
        message: `找到${count}条动态卡片`,
        details: count >= 3 ? undefined : '建议至少包含3条动态以展示不同布局'
      })

      expect(count).toBeGreaterThanOrEqual(3)
    })

    it('图片应具有正确的样式属性', () => {
      const imageStyleRegex = /\.post-image\s*\{[^}]*(?:width:\s*100%[^}]*aspect-ratio[^}]*object-fit[^}]*|aspect-ratio[^}]*width[^}]*object-fit[^}]*|object-fit[^}]*width[^}]*aspect-ratio[^}]*)/gi
      const matches = htmlContent.match(imageStyleRegex)

      if (matches && matches.length > 0) {
        results.imagePresence.push({
          passed: true,
          message: '图片具有正确的样式属性(width/aspect-ratio/object-fit)'
        })
      } else {
        results.imagePresence.push({
          passed: false,
          message: '图片缺少正确的样式属性',
          details: '.post-image应包含width: 100%, aspect-ratio: 1, object-fit: cover'
        })
      }

      expect(matches && matches.length).toBeGreaterThan(0)
    })
  })

  describe('测试结果汇总', () => {
    it('所有测试通过', () => {
      const allResults = [
        ...results.imagePresence,
        ...results.gridLayout,
        ...results.avatarImages,
        ...results.imageUrls
      ]

      const failedResults = allResults.filter(r => !r.passed)
      const passedResults = allResults.filter(r => r.passed)

      console.log('\n' + '='.repeat(60))
      console.log('🖼️  图片预览UI组件测试报告')
      console.log('='.repeat(60))
      console.log('')

      console.log(`📊 总计: ${allResults.length} 项测试`)
      console.log(`✅ 通过: ${passedResults.length}`)
      console.log(`❌ 失败: ${failedResults.length}`)
      console.log('')

      console.log('📸 图片存在性测试:')
      results.imagePresence.forEach(r => {
        const icon = r.passed ? '✅' : '❌'
        console.log(`  ${icon} ${r.message}`)
        if (r.details) console.log(`     💡 ${r.details}`)
      })

      console.log('\n🔲 图片网格布局测试:')
      results.gridLayout.forEach(r => {
        const icon = r.passed ? '✅' : '❌'
        console.log(`  ${icon} ${r.message}`)
        if (r.details) console.log(`     💡 ${r.details}`)
      })

      console.log('\n👤 头像URL测试:')
      results.avatarImages.forEach(r => {
        const icon = r.passed ? '✅' : '❌'
        console.log(`  ${icon} ${r.message}`)
      })

      console.log('\n🖼️ 动态配图URL测试:')
      results.imageUrls.forEach(r => {
        const icon = r.passed ? '✅' : '❌'
        console.log(`  ${icon} ${r.message}`)
        if (r.details) console.log(`     💡 ${r.details}`)
      })

      console.log('\n' + '='.repeat(60))

      expect(failedResults.length).toBe(0)
    })
  })
})
