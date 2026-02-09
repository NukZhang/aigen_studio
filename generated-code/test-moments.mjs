import * as fs from 'fs'
import * as path from 'path'
import { fileURLToPath } from 'url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

class MomentsTestRunner {
  constructor() {
    this.passedCount = 0
    this.failedCount = 0
    this.skippedCount = 0
  }

  async run(testFile = 'moments.html') {
    const htmlPath = path.resolve(__dirname, testFile)

    if (!fs.existsSync(htmlPath)) {
      console.error(`❌ 未找到测试文件: ${testFile}`)
      process.exit(1)
    }

    const htmlContent = fs.readFileSync(htmlPath, 'utf-8')

    console.log(`\n🖼️  开始测试: ${testFile}\n`)

    this.testHeader(htmlContent)
    this.testMomentsFeed(htmlContent)
    this.testImagePresence(htmlContent)
    this.testGridLayout(htmlContent)
    this.testImageUrls(htmlContent)
    this.testImage404(htmlContent)
    this.testInteractions(htmlContent)

    this.printSummary()
  }

  testHeader(html) {
    console.log('📱 测试1: 头部导航组件\n')

    const hasTitle = html.includes('朋友圈')
    const hasSearchBtn = html.includes('搜索') || html.includes('search')
    const hasCameraBtn = html.includes('相机') || html.includes('camera')
    const hasBottomNav = html.includes('bottom-nav') || html.includes('底部导航')

    if (hasTitle) {
      console.log('  ✅ 包含标题"朋友圈"')
      this.passedCount++
    } else {
      console.log('  ❌ 缺少标题')
      this.failedCount++
    }

    if (hasCameraBtn) {
      console.log('  ✅ 包含相机按钮')
      this.passedCount++
    } else {
      console.log('  ⚠️ 缺少相机按钮')
      this.failedCount++
    }

    if (hasSearchBtn) {
      console.log('  ✅ 包含搜索按钮')
      this.passedCount++
    } else {
      console.log('  ⚠️ 缺少搜索按钮')
      this.failedCount++
    }

    if (hasBottomNav) {
      console.log('  ✅ 包含底部导航栏')
      this.passedCount++
    } else {
      console.log('  ❌ 缺少底部导航栏')
      this.failedCount++
    }
    console.log('')
  }

  testMomentsFeed(html) {
    console.log('📰 测试2: 朋友圈动态Feed\n')

    const momentRegex = /<article[^>]+class="[^"]*moment-item[^"]*"[^>]*>/gi
    const matches = html.match(momentRegex)
    const count = matches ? matches.length : 0

    if (count >= 3) {
      console.log(`  ✅ 找到 ${count} 条朋友圈动态（≥3）`)
      this.passedCount++
    } else {
      console.log(`  ❌ 仅找到 ${count} 条动态，建议至少3条`)
      this.failedCount++
    }

    const hasUserInfo = html.includes('user-info')
    if (hasUserInfo) {
      console.log('  ✅ 包含用户信息区域')
      this.passedCount++
    } else {
      console.log('  ⚠️ 缺少用户信息区域')
      this.failedCount++
    }

    console.log('')
  }

  testImagePresence(html) {
    console.log('📸 测试3: 图片存在性\n')

    const avatarRegex = /<img[^>]+class="[^"]*(?:moment-avatar|user-avatar|comment-avatar)[^"]*"[^>]*>/gi
    const matches = html.match(avatarRegex)

    if (matches && matches.length > 0) {
      console.log(`  ✅ 找到 ${matches.length} 个用户头像`)
      this.passedCount++
    } else {
      console.log('  ❌ 缺少用户头像')
      this.failedCount++
    }

    const postImageRegex = /<img[^>]+class="[^"]*moment-image[^"]*"[^>]*>/gi
    const postMatches = html.match(postImageRegex)

    if (postMatches && postMatches.length > 0) {
      console.log(`  ✅ 找到 ${postMatches.length} 张朋友圈配图`)
      this.passedCount++
    } else {
      console.log('  ❌ 缺少朋友圈配图')
      this.failedCount++
    }
    console.log('')
  }

  testGridLayout(html) {
    console.log('🔲 测试4: 图片网格布局\n')

    const singleRegex = /class="[^"]*moment-images[^"]*single[^"]*"/gi
    const doubleRegex = /class="[^"]*moment-images[^"]*double[^"]*"/gi
    const multipleRegex = /class="[^"]*moment-images[^"]*multiple[^"]*"/gi
    const nineRegex = /class="[^"]*moment-images[^"]*nine[^"]*"/gi

    const singleCount = (html.match(singleRegex) || []).length
    const doubleCount = (html.match(doubleRegex) || []).length
    const multipleCount = (html.match(multipleRegex) || []).length
    const nineCount = (html.match(nineRegex) || []).length

    if (singleCount > 0) {
      console.log(`  ✅ 单图布局: ${singleCount}处`)
      this.passedCount++
    }

    if (doubleCount > 0) {
      console.log(`  ✅ 双图布局: ${doubleCount}处`)
      this.passedCount++
    }

    if (multipleCount > 0) {
      console.log(`  ✅ 多图布局(3-8张): ${multipleCount}处`)
      this.passedCount++
    }

    if (nineCount > 0) {
      console.log(`  ✅ 九宫格布局: ${nineCount}处`)
      this.passedCount++
    }

    if (singleCount + doubleCount + multipleCount + nineCount === 0) {
      console.log('  ❌ 未找到任何图片网格布局')
      this.failedCount++
    }

    console.log('')
  }

  testImageUrls(html) {
    console.log('🖼️ 测试5: 图片URL验证\n')

    const avatarRegex = /src="([^"]*dicebear[^"]*)"/gi
    const avatarMatches = [...html.matchAll(avatarRegex)]

    if (avatarMatches.length > 0) {
      console.log(`  ✅ ${avatarMatches.length} 个头像使用DiceBear API`)
      this.passedCount++
    } else {
      console.log('  ❌ 头像未使用DiceBear API')
      this.failedCount++
    }

    const placeholderPatterns = [
      /placeholder/i,
      /via\.placeholder\.com/i,
      /dummyimage/i
    ]

    const validImageRegex = /src="([^"]+(?:images\.unsplash\.com|pexels\.com|picsum\.photos)[^"]*)"/gi
    const validMatches = [...html.matchAll(validImageRegex)]

    if (validMatches.length > 0) {
      console.log(`  ✅ ${validMatches.length} 张图片使用真实URL`)
      this.passedCount++
    } else {
      console.log('  ❌ 未使用真实图片URL')
      this.failedCount++
    }

    const sizedRegex = /src="([^"]*w=\d+[^"]*)"/gi
    const sizedMatches = [...html.matchAll(sizedRegex)]

    if (sizedMatches.length > 0) {
      console.log(`  ✅ ${sizedMatches.length} 张图片包含尺寸参数`)
      this.passedCount++
    } else {
      console.log('  ⚠️ 图片可能缺少尺寸参数')
      this.failedCount++
    }

    console.log('')
  }

  async testImage404(html) {
    console.log('🔍 测试6: 图片404检测\n')

    const allImageRegex = /src="([^"]+)"/gi
    const matches = [...html.matchAll(allImageRegex)]
    const imageUrls = [...new Set(matches.map(m => m[1]))]

    if (imageUrls.length === 0) {
      console.log('  ⚠️ 未找到任何图片URL')
      this.skippedCount++
      console.log('')
      return
    }

    console.log(`  📊 检测 ${imageUrls.length} 个图片URL...`)

    let successCount = 0
    let errorCount = 0
    let skippedUrls = []

    for (const url of imageUrls) {
      try {
        const response = await fetch(url, { method: 'HEAD' })

        if (response.ok) {
          successCount++
        } else if (response.status === 404) {
          console.log(`  ❌ 404错误: ${url.substring(0, 60)}...`)
          errorCount++
        } else {
          console.log(`  ⚠️ HTTP ${response.status}: ${url.substring(0, 50)}...`)
          errorCount++
        }
      } catch (networkError) {
        console.log(`  ⏭️  跳过（网络错误）: ${url.substring(0, 50)}...`)
        skippedUrls.push(url)
        this.skippedCount++
      }

      if (successCount + errorCount > 0 && (successCount + errorCount) % 5 === 0) {
        process.stdout.write(`  📈 进度: ${successCount + errorCount}/${imageUrls.length} `)
      }
    }

    if (errorCount === 0) {
      console.log(`\n  ✅ 所有图片URL均可访问（${successCount}个成功）`)
      this.passedCount++
    } else {
      console.log(`\n  ❌ 发现 ${errorCount} 个404图片`)
      this.failedCount++
    }

    if (skippedUrls.length > 0) {
      console.log(`  ⏭️  跳过 ${skippedUrls.length} 个网络错误`)
    }

    console.log('')
  }

  testInteractions(html) {
    console.log('👍 测试7: 互动功能\n')

    const hasLikes = html.includes('👍') || html.includes('赞')
    const hasComments = html.includes('💬') || html.includes('评论')
    const hasLikeSection = html.includes('like-users') || html.includes('likes')
    const hasCommentSection = html.includes('comments-list') || html.includes('comment-item')

    if (hasLikes) {
      console.log('  ✅ 包含点赞功能')
      this.passedCount++
    } else {
      console.log('  ⚠️ 缺少点赞功能')
      this.failedCount++
    }

    if (hasComments) {
      console.log('  ✅ 包含评论功能')
      this.passedCount++
    } else {
      console.log('  ⚠️ 缺少评论功能')
      this.failedCount++
    }

    if (hasLikeSection) {
      console.log('  ✅ 有点赞用户展示区域')
      this.passedCount++
    }

    if (hasCommentSection) {
      console.log('  ✅ 有评论列表区域')
      this.passedCount++
    }

    const hasLocation = html.includes('moment-location') || html.includes('位置')
    if (hasLocation) {
      console.log('  ✅ 包含位置信息')
      this.passedCount++
    }

    console.log('')
  }

  printSummary() {
    const total = this.passedCount + this.failedCount
    const percentage = Math.round((this.passedCount / total) * 100)

    console.log('='.repeat(60))
    console.log('📊 测试结果汇总')
    console.log('='.repeat(60))
    console.log(`  总计: ${total} 项测试`)
    console.log(`  ✅ 通过: ${this.passedCount}`)
    console.log(`  ❌ 失败: ${this.failedCount}`)
    console.log(`  ⏭️  跳过: ${this.skippedCount}`)
    console.log(`  得分: ${percentage}%\n`)

    if (percentage >= 80 && this.failedCount === 0) {
      console.log('🎉 所有测试通过！\n')
    } else if (percentage >= 80) {
      console.log('⚠️  测试部分通过，请关注失败的测试项\n')
    } else {
      console.log('❌ 测试未通过，请检查并修复失败的测试项\n')
    }

    console.log('='.repeat(60))
    console.log('')
  }
}

const runner = new MomentsTestRunner()
const testFile = process.argv[2] || 'moments.html'
runner.run(testFile).catch(console.error)
