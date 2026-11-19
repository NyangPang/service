import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { fetchDistricts, fetchRanking } from './api'
import type { RankingParams } from './schema'

export const searchKeys = {
  ranking: (p: RankingParams) => ['stores', 'ranking', p] as const,
  districts: ['stores', 'districts'] as const,
}

export function useRanking(params: RankingParams) {
  return useQuery({
    queryKey: searchKeys.ranking(params),
    queryFn: () => fetchRanking(params),
    // 조건을 바꾸는 동안 이전 결과를 보여 줘서 목록이 깜빡이지 않게
    placeholderData: keepPreviousData,
  })
}

export function useDistricts() {
  return useQuery({ queryKey: searchKeys.districts, queryFn: fetchDistricts, staleTime: 5 * 60_000 })
}

/** 입력이 멈춘 뒤에만 값이 바뀜 (검색어를 칠 때마다 요청하지 않게) */
export function useDebounced<T>(value: T, delayMs = 250) {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])
  return debounced
}
