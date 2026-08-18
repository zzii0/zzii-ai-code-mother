/**
 * 可视化编辑器工具类
 * 负责管理 iframe 内的可视化编辑功能
 */
export interface ElementInfo {
  tagName: string
  id: string
  className: string
  textContent: string
  selector: string
  pagePath: string
  rect: {
    top: number
    left: number
    width: number
    height: number
  }
}

export interface InlineEditPayload {
  selector: string
  oldContent: string
  newContent: string
  innerHtml: string
  tagName: string
  file: string
}

export interface VisualEditorOptions {
  supportsInlineEdit?: boolean
  onElementSelected?: (elementInfo: ElementInfo) => void
  onElementHover?: (elementInfo: ElementInfo) => void
  onInlineEditCommit?: (payload: InlineEditPayload) => void
}

export class VisualEditor {
  private iframe: HTMLIFrameElement | null = null
  private isEditMode = false
  private supportsInlineEdit = false
  private options: VisualEditorOptions

  constructor(options: VisualEditorOptions = {}) {
    this.options = options
    this.supportsInlineEdit = options.supportsInlineEdit ?? false
  }

  /**
   * 更新是否支持行内编辑（HTML / 多文件模式）
   */
  setSupportsInlineEdit(supportsInlineEdit: boolean) {
    this.supportsInlineEdit = supportsInlineEdit
    if (this.isEditMode) {
      this.sendMessageToIframe({
        type: 'SET_INLINE_EDIT',
        supportsInlineEdit,
      })
    }
  }

  /**
   * 初始化编辑器
   */
  init(iframe: HTMLIFrameElement) {
    this.iframe = iframe
  }

  /**
   * 开启编辑模式
   */
  enableEditMode() {
    if (!this.iframe) {
      return
    }
    this.isEditMode = true
    setTimeout(() => {
      this.injectEditScript()
    }, 300)
  }

  /**
   * 关闭编辑模式
   */
  disableEditMode() {
    this.isEditMode = false
    this.sendMessageToIframe({
      type: 'TOGGLE_EDIT_MODE',
      editMode: false,
      supportsInlineEdit: this.supportsInlineEdit,
    })
    this.sendMessageToIframe({
      type: 'CLEAR_ALL_EFFECTS',
    })
  }

  /**
   * 切换编辑模式
   */
  toggleEditMode() {
    if (this.isEditMode) {
      this.disableEditMode()
    } else {
      this.enableEditMode()
    }
    return this.isEditMode
  }

  /**
   * 强制同步状态并清理
   */
  syncState() {
    if (!this.isEditMode) {
      this.sendMessageToIframe({
        type: 'CLEAR_ALL_EFFECTS',
      })
    }
  }

  /**
   * 清除选中的元素
   */
  clearSelection() {
    this.sendMessageToIframe({
      type: 'CLEAR_SELECTION',
    })
  }

  /**
   * iframe 加载完成时调用
   */
  onIframeLoad() {
    if (this.isEditMode) {
      setTimeout(() => {
        this.injectEditScript()
      }, 500)
    } else {
      setTimeout(() => {
        this.syncState()
      }, 500)
    }
  }

  /**
   * 处理来自 iframe 的消息
   */
  handleIframeMessage(event: MessageEvent) {
    const { type, data } = event.data
    switch (type) {
      case 'ELEMENT_SELECTED':
        if (this.options.onElementSelected && data.elementInfo) {
          this.options.onElementSelected(data.elementInfo)
        }
        break
      case 'ELEMENT_HOVER':
        if (this.options.onElementHover && data.elementInfo) {
          this.options.onElementHover(data.elementInfo)
        }
        break
      case 'INLINE_EDIT_COMMIT':
        if (this.options.onInlineEditCommit && data.inlineEdit) {
          this.options.onInlineEditCommit(data.inlineEdit)
        }
        break
    }
  }

  /**
   * 向 iframe 发送消息
   */
  private sendMessageToIframe(message: Record<string, unknown>) {
    if (this.iframe?.contentWindow) {
      this.iframe.contentWindow.postMessage(message, '*')
    }
  }

  /**
   * 注入编辑脚本到 iframe
   */
  private injectEditScript() {
    if (!this.iframe) return

    const waitForIframeLoad = () => {
      try {
        if (this.iframe!.contentWindow && this.iframe!.contentDocument) {
          const existingScript = this.iframe!.contentDocument.getElementById('visual-edit-script')
          if (existingScript) {
            this.sendMessageToIframe({
              type: 'TOGGLE_EDIT_MODE',
              editMode: true,
              supportsInlineEdit: this.supportsInlineEdit,
            })
            return
          }

          const script = this.generateEditScript(this.supportsInlineEdit)
          const scriptElement = this.iframe!.contentDocument.createElement('script')
          scriptElement.id = 'visual-edit-script'
          scriptElement.textContent = script
          this.iframe!.contentDocument.head.appendChild(scriptElement)
        } else {
          setTimeout(waitForIframeLoad, 100)
        }
      } catch {
        // 静默处理注入失败
      }
    }

    waitForIframeLoad()
  }

  /**
   * 生成编辑脚本内容
   */
  private generateEditScript(supportsInlineEdit: boolean) {
    return `
      (function() {
        let isEditMode = true;
        let supportsInlineEdit = ${supportsInlineEdit};
        let currentHoverElement = null;
        let currentSelectedElement = null;
        let currentInlineEditingElement = null;
        let clickTimer = null;

        const INLINE_EDITABLE_TAGS = new Set([
          'H1', 'H2', 'H3', 'H4', 'H5', 'H6',
          'P', 'SPAN', 'A', 'BUTTON', 'LABEL', 'LI',
          'TD', 'TH', 'FIGCAPTION', 'BLOCKQUOTE',
          'EM', 'STRONG', 'B', 'I', 'SMALL', 'CAPTION'
        ]);

        function injectStyles() {
          if (document.getElementById('edit-mode-styles')) return;
          const style = document.createElement('style');
          style.id = 'edit-mode-styles';
          style.textContent = \`
            .edit-hover {
              outline: 2px dashed #1890ff !important;
              outline-offset: 2px !important;
              cursor: crosshair !important;
              transition: outline 0.2s ease !important;
              position: relative !important;
            }
            .edit-hover::before {
              content: '' !important;
              position: absolute !important;
              top: -4px !important;
              left: -4px !important;
              right: -4px !important;
              bottom: -4px !important;
              background: rgba(24, 144, 255, 0.02) !important;
              pointer-events: none !important;
              z-index: -1 !important;
            }
            .edit-selected {
              outline: 3px solid #52c41a !important;
              outline-offset: 2px !important;
              cursor: default !important;
              position: relative !important;
            }
            .edit-selected::before {
              content: '' !important;
              position: absolute !important;
              top: -4px !important;
              left: -4px !important;
              right: -4px !important;
              bottom: -4px !important;
              background: rgba(82, 196, 26, 0.03) !important;
              pointer-events: none !important;
              z-index: -1 !important;
            }
            .inline-editable-hover {
              outline: 2px dashed #1890ff !important;
              outline-offset: 2px !important;
              cursor: text !important;
            }
            .inline-editing {
              outline: 2px solid #1890ff !important;
              outline-offset: 2px !important;
              cursor: text !important;
              position: relative !important;
            }
          \`;
          document.head.appendChild(style);
        }

        function isEditableElement(element) {
          if (!element || !element.tagName) return false;
          if (element === document.body || element === document.documentElement) return false;
          if (element.tagName === 'SCRIPT' || element.tagName === 'STYLE') return false;
          return INLINE_EDITABLE_TAGS.has(element.tagName);
        }

        function isEditorClass(className) {
          return className.startsWith('edit-') || className === 'inline-editing' || className === 'inline-editable-hover';
        }

        // 生成元素选择器
        function generateSelector(element) {
          const path = [];
          let current = element;
          while (current && current !== document.body) {
            let selector = current.tagName.toLowerCase();
            if (current.id) {
              selector += '#' + current.id;
              path.unshift(selector);
              break;
            }
            if (current.className) {
              const classes = current.className.split(' ').filter(c => c && !isEditorClass(c));
              if (classes.length > 0) {
                selector += '.' + classes.join('.');
              }
            }
            const siblings = Array.from(current.parentElement?.children || []);
            const index = siblings.indexOf(current) + 1;
            selector += ':nth-child(' + index + ')';
            path.unshift(selector);
            current = current.parentElement;
          }
          return path.join(' > ');
        }

        // 获取元素信息
        function getElementInfo(element) {
          const rect = element.getBoundingClientRect();
          let pagePath = window.location.search + window.location.hash;
          if (!pagePath) {
            pagePath = '';
          }

          return {
            tagName: element.tagName,
            id: element.id,
            className: element.className,
            textContent: element.textContent?.trim().substring(0, 100) || '',
            selector: generateSelector(element),
            pagePath: pagePath,
            rect: {
              top: rect.top,
              left: rect.left,
              width: rect.width,
              height: rect.height
            }
          };
        }

        function clearHoverEffect() {
          if (currentHoverElement) {
            currentHoverElement.classList.remove('edit-hover', 'inline-editable-hover');
            currentHoverElement = null;
          }
        }

        function clearSelectedEffect() {
          const selected = document.querySelectorAll('.edit-selected');
          selected.forEach(el => el.classList.remove('edit-selected'));
          currentSelectedElement = null;
        }

        function getCleanInnerHtml(element) {
          const clone = element.cloneNode(true);
          clone.classList.remove('edit-hover', 'edit-selected', 'inline-editing', 'inline-editable-hover');
          clone.querySelectorAll('[class]').forEach((node) => {
            const classes = Array.from(node.classList).filter((c) => !isEditorClass(c));
            if (classes.length > 0) {
              node.className = classes.join(' ');
            } else {
              node.removeAttribute('class');
            }
          });
          return clone.innerHTML;
        }

        function finishInlineEditing() {
          if (!currentInlineEditingElement) return;

          const element = currentInlineEditingElement;
          const originalText = element.dataset.inlineEditOriginal || '';
          const originalHtml = element.dataset.inlineEditOriginalHtml || '';
          const newText = element.textContent?.trim() || '';
          const newHtml = getCleanInnerHtml(element);

          element.contentEditable = 'false';
          element.classList.remove('inline-editing');
          element.removeEventListener('blur', inlineEditBlurHandler);
          element.removeEventListener('keydown', inlineEditKeydownHandler);
          delete element.dataset.inlineEditOriginal;
          delete element.dataset.inlineEditOriginalHtml;
          currentInlineEditingElement = null;

          if (newText !== originalText || newHtml !== originalHtml) {
            try {
              window.parent.postMessage({
                type: 'INLINE_EDIT_COMMIT',
                data: {
                  inlineEdit: {
                    selector: generateSelector(element),
                    oldContent: originalText,
                    newContent: newText,
                    innerHtml: newHtml,
                    tagName: element.tagName,
                    file: 'index.html'
                  }
                }
              }, '*');
            } catch {
              // 静默处理发送失败
            }
          }
        }

        function inlineEditBlurHandler(event) {
          if (!currentInlineEditingElement) return;
          if (event.target !== currentInlineEditingElement) return;
          finishInlineEditing();
        }

        function inlineEditKeydownHandler(event) {
          if (!currentInlineEditingElement) return;
          if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault();
            currentInlineEditingElement.blur();
          }
          if (event.key === 'Escape') {
            const element = currentInlineEditingElement;
            if (element.dataset.inlineEditOriginalHtml !== undefined) {
              element.innerHTML = element.dataset.inlineEditOriginalHtml;
            } else {
              element.textContent = element.dataset.inlineEditOriginal || '';
            }
            element.blur();
          }
        }

        function startInlineEditing(target) {
          if (!supportsInlineEdit || !isEditableElement(target)) return false;
          if (currentInlineEditingElement === target) return true;

          finishInlineEditing();
          clearSelectedEffect();
          clearHoverEffect();

          target.dataset.inlineEditOriginal = target.textContent?.trim() || '';
          target.dataset.inlineEditOriginalHtml = target.innerHTML;
          target.contentEditable = 'true';
          target.classList.add('inline-editing');
          target.addEventListener('blur', inlineEditBlurHandler);
          target.addEventListener('keydown', inlineEditKeydownHandler);
          currentInlineEditingElement = target;
          currentSelectedElement = null;
          target.focus();

          const selection = window.getSelection();
          if (selection) {
            const range = document.createRange();
            range.selectNodeContents(target);
            range.collapse(false);
            selection.removeAllRanges();
            selection.addRange(range);
          }
          return true;
        }

        let eventListenersAdded = false;

        function addEventListeners() {
           if (eventListenersAdded) return;

           const mouseoverHandler = (event) => {
             if (!isEditMode) return;
             if (currentInlineEditingElement) return;

             const target = event.target;
             if (target === currentHoverElement || target === currentSelectedElement) return;
             if (target === document.body || target === document.documentElement) return;
             if (target.tagName === 'SCRIPT' || target.tagName === 'STYLE') return;

             clearHoverEffect();
             if (supportsInlineEdit && isEditableElement(target)) {
               target.classList.add('inline-editable-hover');
             } else {
               target.classList.add('edit-hover');
             }
             currentHoverElement = target;
           };

           const mouseoutHandler = (event) => {
             if (!isEditMode) return;

             const target = event.target;
             if (!event.relatedTarget || !target.contains(event.relatedTarget)) {
               clearHoverEffect();
             }
           };

           const clickHandler = (event) => {
             if (!isEditMode) return;
             if (currentInlineEditingElement) return;

             event.preventDefault();
             event.stopPropagation();

             const target = event.target;
             if (target === document.body || target === document.documentElement) return;
             if (target.tagName === 'SCRIPT' || target.tagName === 'STYLE') return;

             if (clickTimer) {
               clearTimeout(clickTimer);
             }

             clickTimer = setTimeout(() => {
               clickTimer = null;
               clearSelectedEffect();
               clearHoverEffect();

               target.classList.add('edit-selected');
               currentSelectedElement = target;

               const elementInfo = getElementInfo(target);
               try {
                 window.parent.postMessage({
                   type: 'ELEMENT_SELECTED',
                   data: { elementInfo }
                 }, '*');
               } catch {
                 // 静默处理发送失败
               }
             }, 250);
           };

           const dblClickHandler = (event) => {
             if (!isEditMode || !supportsInlineEdit) return;

             event.preventDefault();
             event.stopPropagation();

             if (clickTimer) {
               clearTimeout(clickTimer);
               clickTimer = null;
             }

             const target = event.target;
             if (startInlineEditing(target)) {
               event.stopImmediatePropagation();
             }
           };

           document.body.addEventListener('mouseover', mouseoverHandler, true);
           document.body.addEventListener('mouseout', mouseoutHandler, true);
           document.body.addEventListener('click', clickHandler, true);
           document.body.addEventListener('dblclick', dblClickHandler, true);
           eventListenersAdded = true;
         }

         function setupEventListeners() {
           addEventListeners();
         }

        // 监听父窗口消息
        window.addEventListener('message', (event) => {
           const { type, editMode, supportsInlineEdit: inlineEditEnabled } = event.data;
           switch (type) {
             case 'TOGGLE_EDIT_MODE':
               isEditMode = editMode;
               if (typeof inlineEditEnabled === 'boolean') {
                 supportsInlineEdit = inlineEditEnabled;
               }
               if (isEditMode) {
                 injectStyles();
                 setupEventListeners();
                 showEditTip();
               } else {
                 finishInlineEditing();
                 clearHoverEffect();
                 clearSelectedEffect();
               }
               break;
             case 'SET_INLINE_EDIT':
               supportsInlineEdit = !!inlineEditEnabled;
               break;
             case 'CLEAR_SELECTION':
               clearSelectedEffect();
               break;
             case 'CLEAR_ALL_EFFECTS':
               isEditMode = false;
               finishInlineEditing();
               clearHoverEffect();
               clearSelectedEffect();
               const tip = document.getElementById('edit-tip');
               if (tip) tip.remove();
               break;
           }
         });

         function showEditTip() {
           if (document.getElementById('edit-tip')) return;
           const tip = document.createElement('div');
           tip.id = 'edit-tip';
           tip.innerHTML = supportsInlineEdit
             ? '🎯 编辑模式已开启<br/>单击选中元素，双击文本可直接修改'
             : '🎯 编辑模式已开启<br/>悬浮查看元素，点击选中元素';
           tip.style.cssText = \`
             position: fixed;
             top: 20px;
             right: 20px;
             background: #1890ff;
             color: white;
             padding: 12px 16px;
             border-radius: 6px;
             font-size: 14px;
             z-index: 9999;
             box-shadow: 0 4px 12px rgba(0,0,0,0.15);
             animation: fadeIn 0.3s ease;
           \`;
           const style = document.createElement('style');
           style.textContent = '@keyframes fadeIn { from { opacity: 0; transform: translateY(-10px); } to { opacity: 1; transform: translateY(0); } }';
           document.head.appendChild(style);
           document.body.appendChild(tip);
           setTimeout(() => {
             if (tip.parentNode) {
               tip.style.animation = 'fadeIn 0.3s ease reverse';
               setTimeout(() => tip.remove(), 300);
             }
           }, 3000);
         }
         injectStyles();
         setupEventListeners();
         showEditTip();
      })();
    `
  }
}
