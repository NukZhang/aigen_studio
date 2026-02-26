export const workspaceTestStubs = {
  'el-button': {
    props: ['disabled'],
    template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
  },
  'el-icon': { template: '<i><slot /></i>' },
  'el-drawer': { template: '<div><slot /></div>' },
  'el-tabs': { template: '<div><slot /></div>' },
  'el-tab-pane': { template: '<div><slot /></div>' },
  'el-dropdown': { template: '<div><slot /></div>' },
  'el-dropdown-menu': { template: '<div><slot /></div>' },
  'el-dropdown-item': { template: '<div><slot /></div>' },
  'el-upload': { template: '<div><slot /></div>' },
  'el-tree': { template: '<div><slot /></div>' },
  'el-input': {
    props: ['modelValue', 'disabled', 'size', 'type', 'rows', 'placeholder'],
    template: `
      <component
        :is="type === 'textarea' ? 'textarea' : 'input'"
        :value="modelValue"
        :disabled="disabled"
        @input="$emit('update:modelValue', $event.target.value)"
      />
    `
  },
  PreviewPanel: { template: '<div />' },
  FileBrowser: { template: '<div />' },
  CodeEditor: { template: '<div />' },
  UIPrototypePanel: { template: '<div />' }
}
