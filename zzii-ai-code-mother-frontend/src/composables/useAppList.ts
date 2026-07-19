import { reactive, ref } from 'vue'
import { listMyAppVoByPage, listGoodAppVoByPage } from '@/api/appController'
import { getApiData } from '@/utils/apiHelper'

export function useAppList() {
  const myApps = ref<API.AppVO[]>([])
  const myAppsLoading = ref(false)
  const myAppsPage = reactive({
    current: 1,
    pageSize: 6,
    total: 0,
  })

  const featuredApps = ref<API.AppVO[]>([])
  const featuredAppsLoading = ref(false)
  const featuredAppsPage = reactive({
    current: 1,
    pageSize: 6,
    total: 0,
  })

  const loadMyApps = async (userId?: number) => {
    if (!userId) {
      myApps.value = []
      myAppsPage.total = 0
      return
    }

    myAppsLoading.value = true
    try {
      const res = await listMyAppVoByPage({
        pageNum: myAppsPage.current,
        pageSize: myAppsPage.pageSize,
        sortField: 'createTime',
        sortOrder: 'desc',
      })
      const pageData = getApiData(res)
      myApps.value = pageData?.records || []
      myAppsPage.total = pageData?.totalRow || 0
    } catch (error) {
      console.error('加载我的应用失败：', error)
    } finally {
      myAppsLoading.value = false
    }
  }

  const loadFeaturedApps = async () => {
    featuredAppsLoading.value = true
    try {
      const res = await listGoodAppVoByPage({
        pageNum: featuredAppsPage.current,
        pageSize: featuredAppsPage.pageSize,
        sortField: 'createTime',
        sortOrder: 'desc',
      })
      const pageData = getApiData(res)
      featuredApps.value = pageData?.records || []
      featuredAppsPage.total = pageData?.totalRow || 0
    } catch (error) {
      console.error('加载精选应用失败：', error)
    } finally {
      featuredAppsLoading.value = false
    }
  }

  return {
    myApps,
    myAppsLoading,
    myAppsPage,
    featuredApps,
    featuredAppsLoading,
    featuredAppsPage,
    loadMyApps,
    loadFeaturedApps,
  }
}
