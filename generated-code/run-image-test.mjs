import * as fs from 'fs'
import * as path from 'path'
import { fileURLToPath } from 'url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

class TestRunner {
  constructor() {
    this.passedCount = 0
    this.failedCount = 0
  }

  async run() {
    const htmlPath = path.resolve(__dirname, 'index.html')

    if (!fs.existsSync(htmlPath)) {
      console.error('❌ 未找到测试文件: index.html')
      process.exit(1)
    }

    const htmlContent = fs.readFileSync(htmlPath, 'utf-8')

    console.log('\n🖼️  开始图片预览UI组件测试...\n')

    this.testImagePresence(htmlContent)
    this.testGridLayout(htmlContent)
    this.testImageUrls(htmlContent)
    this.testComponentIntegrity(htmlContent)

    this.printSummary()
  }

  testImagePresence(html) {
    console.log('📸 测试1: 图片存在性\n')

    const avatarRegex = /<img[^>]+class="[^"]*(?:avatar|user-avatar|comment-avatar)[^"]*"[^>]*>/gi
    const matches = html.match(avatarRegex)

    if (!matches || matches.length === 0) {
      console.log('  ❌ 缺少用户头像图片')
      console.log('     💡 未找到.avatar, .user-avatar或.comment-avatar类名的img元素\n')
      this.failedCount++
      return
    }

    matches.forEach((match) => {
      const srcMatch = match.match(/src="([^"]+)"/)
      if (srcMatch && srcMatch[1]) {
        console.log(`  ✅ 找到用户头像: ${srcMatch[1].substring(0, 60)}...`)
        this.passedCount++
      }
    })
    console.log('')

    const postImageRegex = /<img[^>]+class="[^"]*post-image[^"]*"[^>]*>/gi
    const postMatches = html.match(postImageRegex)

    if (!postMatches || postMatches.length === 0) {
      console.log('  ❌ 缺少动态配图')
      console.log('     💡 未找到.post-image类名的img元素\n')
      this.failedCount++
      return
    }

    postMatches.slice(0, 3).forEach((match) => {
      const srcMatch = match.match(/src="([^"]+)"/)
      if (srcMatch && srcMatch[1]) {
        console.log(`  ✅ 找到动态配图: ${srcMatch[1].substring(0, 60)}...`)
        this.passedCount++
      }
    })

    if (postMatches.length > 3) {
      console.log(`     ... 还有 ${postMatches.length - 3} 张图片`)
    }
    console.log('')
  }

  testGridLayout(html) {
    console.log('🔲 测试2: 图片网格布局\n')

    const containerRegex = /<div[^>]+class="[^"]*post-images[^"]*(?:single|double|multiple)?[^"]*"[^>]*>/gi
    const matches = html.match(containerRegex)

    if (!matches || matches.length === 0) {
      console.log('  ❌ 缺少图片容器')
      console.log('     💡 未找到.post-images类名的容器元素\n')
      this.failedCount++
      return
    }

    matches.forEach((match) => {
      if (/class="[^"]*single[^"]*"/.test(match)) {
        console.log('  ✅ 找到单图布局容器: .single')
        this.passedCount++
      } else if (/class="[^"]*double[^"]*"/.test(match)) {
        console.log('  ✅ 找到双图布局容器: .double')
        this.passedCount++
      } else if (/class="[^"]*multiple[^"]*"/.test(match)) {
        console.log('  ✅ 找到多图布局容器: .multiple')
        this.passedCount++
      }
    })
    console.log('')

    const singleStyleRegex = /\.post-images\.single\s*\{[^}]*grid-template-columns:\s*1fr[^}]*\}/gi
    if (html.match(singleStyleRegex)) {
      console.log('  ✅ 单图CSS正确: grid-template-columns: 1fr')
      this.passedCount++
    } else {
      console.log('  ❌ 缺少单图CSS样式')
      this.failedCount++
    }

    const multipleStyleRegex = /\.post-images\.multiple\s*\{[^}]*grid-template-columns:\s*repeat\(\s*3\s*,\s*1fr\)[^}]*\}/gi
    if (html.match(multipleStyleRegex)) {
      console.log('  ✅ 多图CSS正确: grid-template-columns: repeat(3, 1fr)')
      this.passedCount++
    } else {
      console.log('  ❌ 缺少多图CSS样式')
      this.failedCount++
    }
    console.log('')
  }

  testImageUrls(html) {
    console.log('🖼️ 测试3: 图片URL验证\n')

    const avatarRegex = /src="([^"]*dicebear[^"]*)"/gi
    const avatarMatches = [...html.matchAll(avatarRegex)]

    if (avatarMatches.length > 0) {
      console.log(`  ✅ ${avatarMatches.length} 个头像使用DiceBear API`)
      this.passedCount++
    } else {
      console.log('  ❌ 头像未使用DiceBear API')
      console.log('     💡 建议使用 https://api.dicebear.com/7.x/avataaars/svg 生成头像\n')
      this.failedCount++
    }

    const postImageRegex = /src="([^"]+(?:images\.unsplash\.com|pexels\.com|picsum\.photos)[^"]*)"/gi
    const validMatches = [...html.matchAll(postImageRegex)]

    if (validMatches.length > 0) {
      console.log(`  ✅ ${validMatches.length} 张图片使用真实URL（而非占位符）`)
      this.passedCount++
    } else {
      console.log('  ⚠️ 未找到知名图片服务URL，请确保使用真实图片\n')
    }

    const sizedRegex = /src="([^"]*(?:w=|width=)[^"]*)"/gi
    const sizedMatches = [...html.matchAll(sizedRegex)]

    if (sizedMatches.length > 0) {
      console.log(`  ✅ ${sizedMatches.length} 张图片包含尺寸参数`)
      this.passedCount++
    } else {
      console.log('  ⚠️ 图片可能缺少尺寸参数，建议添加w=400等\n')
    }
    console.log('')
  }

  testComponentIntegrity(html) {
    console.log('📦 测试4: 组件完整性\n')

    const postCardRegex = /<article[^>]+class="[^"]*post-card[^"]*"[^>]*>/gi
    const cardMatches = html.match(postCardRegex)
    const cardCount = cardMatches ? cardMatches.length : 0

    if (cardCount >= 3) {
      console.log(`  ✅ 找到 ${cardCount} 条动态卡片（≥3）`)
      this.passedCount++
    } else {
      console.log(`  ⚠️ 仅找到 ${cardCount} 条动态卡片，建议至少3条\n`)
    }

    const hasLike = html.includes('赞') || html.includes('点赞')
    const hasComment = html.includes('评论')
    const hasShare = html.includes('分享')

    console.log(`  ${hasLike ? '✅' : '❌'} 点赞功能: ${hasLike ? '存在' : '缺失'}`)
    console.log(`  ${hasComment ? '✅' : '❌'} 评论功能: ${hasComment ? '存在' : '缺失'}`)
    console.log(`  ${hasShare ? '✅' : '❌'} 分享功能: ${hasShare ? '存在' : '缺失'}`)

    if (hasLike && hasComment && hasShare) {
      this.passedCount++
    } else {
      this.failedCount++
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
    console.log(`  得分: ${percentage}%\n`)

    if (percentage >= 80) {
      console.log('🎉 图片预览UI组件测试通过！\n')
    } else if (percentage >= 60) {
      console.log('⚠️  测试部分通过，请关注失败的测试项\n')
    } else {
      console.log('❌ 测试未通过，请检查并修复失败的测试项\n')
    }

    console.log('='.repeat(60))
    console.log('')
  }
}

const runner = new TestRunner()
runner.run().catch(console.error)
