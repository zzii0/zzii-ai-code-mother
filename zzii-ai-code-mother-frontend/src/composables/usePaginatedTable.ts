import { computed, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { AxiosResponse } from 'axios'
import { getApiErrorMessage, isApiSuccess } from '@/utils/apiHelper'

type PageData<T> = {
  records?: T[]
  totalRow?: number
}

type PaginatedParams = {
  pageNum?: number
  pageSize?: number
}

export function usePaginatedTable<T, P extends PaginatedParams>(
  fetchFn: (params: P) => Promise<AxiosResponse<{ code?: number; data?: PageData<T>; message?: string }>>,
  initialParams: P,
) {
  const data = ref<T[]>([]) as ReturnType<typeof ref<T[]>>
  const total = ref(0)
  const loading = ref(false)
  const searchParams = reactive({ ...initialParams })

  const fetchData = async () => {
    loading.value = true
    try {
      const res = await fetchFn({ ...searchParams } as P)
      if (isApiSuccess(res) && res.data.data) {
        data.value = res.data.data.records ?? []
        total.value = res.data.data.totalRow ?? 0
      } else {
        message.error('获取数据失败，' + getApiErrorMessage(res))
      }
    } catch (error) {
      console.error('获取数据失败：', error)
      message.error('获取数据失败')
    } finally {
      loading.value = false
    }
  }

  const pagination = computed(() => ({
    current: searchParams.pageNum ?? 1,
    pageSize: searchParams.pageSize ?? 10,
    total: total.value,
    showSizeChanger: true,
    showTotal: (count: number) => `共 ${count} 条`,
  }))

  const doTableChange = (page: { current: number; pageSize: number }) => {
    searchParams.pageNum = page.current
    searchParams.pageSize = page.pageSize
    fetchData()
  }

  const doSearch = () => {
    searchParams.pageNum = 1
    fetchData()
  }

  return {
    data,
    total,
    loading,
    searchParams,
    pagination,
    fetchData,
    doTableChange,
    doSearch,
  }
}
