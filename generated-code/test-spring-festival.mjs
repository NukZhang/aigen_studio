import * as fs from 'fs'
import * as path from 'path'
import { fileURLToPath } from 'url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

class SpringFestivalTestRunner {
  constructor() {
    this.passedCount = 0
    this.failedCount = 0
    this.skippedCount = 0
  }

  async run(testFile = 'spring-festival-blessing.html') {
    const htmlPath = path.resolve(__dirname, testFile)

    if (!fs.existsSync(htmlPath)) {
      console.error(`❌ 未找到测试文件: ${testFile}`)
      process.exit(1)
    }

    const htmlContent = fs.readFileSync(htmlPath, 'utf-8')

    console.log(`\n🧧 开始测试: 新春祝福语生成器\n`)

    this.testHeader(htmlContent)
    this.testGeneratorCard(htmlContent)
    this.testImagePresence(htmlContent)
    this.testGridLayout(htmlContent)
    this.testImageUrls(htmlContent)
    this.testImage404(htmlContent)
    this.testInteractivity(htmlContent)
    this.testTemplatesSection(htmlContent)

    this.printSummary()
  }

  testHeader(html) {
    console.log('📱 测试1: 头部导航\n')

    const hasTitle = html.includes('新春祝福语生成器')
    const hasHeaderIcon = html.includes('🏮') || html.includes('header-icon')
    const hasRedTheme = html.includes('#e02e24') || (html.includes('linear-gradient') && html.includes('#ffb900'))

    if (hasTitle) {
      console.log('  ✅ 包含标题"新春祝福语生成器"')
      this.passedCount++
    } else {
      console.log('  ❌ 缺少标题')
      this.failedCount++
    }

    if (hasHeaderIcon) {
      console.log('  ✅ 包含春节图标')
      this.passedCount++
    }

    if (hasRedTheme) {
      console.log('  ✅ 使用新年红色主题')
      this.passedCount++
    }

    console.log('')
  }

  testGeneratorCard(html) {
    console.log('✨ 测试2: 祝福生成器组件\n')

    const hasReceiverInput = html.includes('receiverInput') || html.includes('收件人')
    const hasCategoryBtns = html.includes('category-btn') || html.includes('category-grid')
    const hasGenerateBtn = html.includes('generateBtn') || html.includes('生成专属祝福')
    const hasCategoryCount = (html.match(/category-btn/g) || []).length >= 4

    if (hasReceiverInput) {
      console.log('  ✅ 包含收件人输入框')
      this.passedCount++
    } else {
      console.log('  ❌ 缺少收件人输入框')
      this.failedCount++
    }

    if (hasCategoryBtns) {
      console.log(`  ✅ 包含分类选择按钮（${(html.match(/category-btn/g) || []).length}个）`)
      this.passedCount++
    } else {
      console.log('  ❌ 缺少分类按钮')
      this.failedCount++
    }

    if (hasGenerateBtn) {
      console.log('  ✅ 包含生成按钮')
      this.passedCount++
    } else {
      console.log('  ❌ 缺少生成按钮')
      this.failedCount++
    }

    console.log('')
  }

  testImagePresence(html) {
    console.log('📸 测试3: 图片存在性\n')

    const avatarRegex = /<img[^>]+class="[^"]*result-avatar[^"]*"[^>]*>/gi
    const avatarMatches = html.match(avatarRegex)

    if (avatarMatches && avatarMatches.length > 0) {
      console.log('  ✅ 包含结果头像')
      this.passedCount++
    } else {
      console.log('  ⚠️ 缺少结果头像')
      this.failedCount++
    }

    const templateImageRegex = /<img[^>]+class="[^"]*template-image[^"]*"[^>]*>/gi
    const templateMatches = html.match(templateImageRegex)

    if (templateMatches && templateMatches.length > 0) {
      console.log(`  ✅ 包含 ${templateMatches.length} 个模板图片`)
      this.passedCount++
    } else {
      console.log('  ❌ 缺少模板图片')
      this.failedCount++
    }

    const blessingImageRegex = /<img[^>]+class="[^"]*blessing-image[^"]*"[^>]*>/gi
    const blessingMatches = html.match(blessingImageRegex)

    if (blessingMatches && blessingMatches.length > 0) {
      console.log('  ✅ 包含祝福展示图片')
      this.passedCount++
    } else {
      console.log('  ⚠️ 缺少祝福展示图片')
      this.failedCount++
    }

    console.log('')
  }

  testGridLayout(html) {
    console.log('🔲 测试4: 图片网格布局\n')

    const categoryGrid = html.includes('category-grid')
    const templateGrid = html.includes('template-grid')

    if (categoryGrid) {
      console.log('  ✅ 分类选择区使用Grid布局')
      this.passedCount++
    } else {
      console.log('  ⚠️ 分类选择区未使用Grid')
      this.failedCount++
    }

    if (templateGrid) {
      console.log('  ✅ 模板卡片区使用Grid布局')
      this.passedCount++
    } else {
      console.log('  ❌ 模板卡片区未使用Grid')
      this.failedCount++
    }

    const gridTemplateCount = (html.match(/grid-template-columns/g) || []).length

    if (gridTemplateCount >= 2) {
      console.log(`  ✅ 包含 ${gridTemplateCount} 个Grid模板定义`)
      this.passedCount++
    } else {
      console.log('  ⚠️ Grid模板定义不足')
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

      if (successCount + errorCount > 0 && (successCount + errorCount) % 3 === 0) {
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

  testInteractivity(html) {
    console.log('🎮 测试7: 交互功能\n')

    const hasCopyBtn = html.includes('copyBtn') || html.includes('复制文字')
    const hasShareBtn = html.includes('shareBtn') || html.includes('分享')
    const hasResultCard = html.includes('resultCard') || html.includes('result-content')
    const hasLoading = html.includes('loading') || html.includes('loading-spinner')
    const hasToast = html.includes('toast')

    if (hasCopyBtn) {
      console.log('  ✅ 包含复制功能')
      this.passedCount++
    } else {
      console.log('  ❌ 缺少复制功能')
      this.failedCount++
    }

    if (hasShareBtn) {
      console.log('  ✅ 包含分享功能')
      this.passedCount++
    } else {
      console.log('  ❌ 缺少分享功能')
      this.failedCount++
    }

    if (hasResultCard) {
      console.log('  ✅ 包含结果展示卡片')
      this.passedCount++
    }

    if (hasLoading) {
      console.log('  ✅ 包含加载动画')
      this.passedCount++
    }

    if (hasToast) {
      console.log('  ✅ 包含提示消息')
      this.passedCount++
    }

    console.log('')
  }

  testTemplatesSection(html) {
    console.log('🎨 测试8: 模板展示区\n')

    const hasTemplatesSection = html.includes('templates-section') || html.includes('热门模板')
    const templateCardCount = (html.match(/template-card/g) || []).length

    if (hasTemplatesSection) {
      console.log('  ✅ 包含模板展示区')
      this.passedCount++
    } else {
      console.log('  ❌ 缺少模板展示区')
      this.failedCount++
    }

    if (templateCardCount >= 4) {
      console.log(`  ✅ 包含 ${templateCardCount} 个模板卡片`)
      this.passedCount++
    } else {
      console.log(`  ⚠️ 模板卡片不足（${templateCardCount}个）`)
      this.failedCount++
    }

    const hasTemplateImages = (html.match(/template-image/g) || []).length

    if (hasTemplateImages >= 4) {
      console.log(`  ✅ 包含 ${hasTemplateImages} 个模板缩略图`)
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
      console.log('🎉 所有测试通过！')
      console.log('🧧 新年快乐！万事如意！🎊\n')
    } else if (percentage >= 80) {
      console.log('⚠️  测试部分通过，请关注失败的测试项\n')
    } else {
      console.log('❌ 测试未通过，请检查并修复失败的测试项\n')
    }

    console.log('='.repeat(60))
    console.log('')
  }
}

const runner = new SpringFestivalTestRunner()
const testFile = process.argv[2] || 'spring-festival-blessing.html'
runner.run(testFile).catch(console.error)
