import api from './index'

export interface TutorialNode {
  name: string
  path: string
  type: 'file' | 'directory'
  children?: TutorialNode[]
}

export interface TutorialContent {
  path: string
  content: string
}

/**
 * 获取教程目录树
 */
export const getTutorialTree = () => {
  return api.get<TutorialNode[]>('/tutorials/tree')
}

/**
 * 获取教程文件内容
 */
export const getTutorialContent = (path: string) => {
  return api.get<TutorialContent>('/tutorials/content', {
    params: { path }
  })
}