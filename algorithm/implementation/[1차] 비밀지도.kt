// 프로그래머스
// 2018 KAKAO BLIND RECRUITMENT > [1차] 비밀지도 (코틀린 풀이)
// https://school.programmers.co.kr/learn/courses/30/lessons/17681

class Solution {
    fun solution(n: Int, arr1: IntArray, arr2: IntArray): Array<String> {

        val answer = mutableListOf<String>()

        for (i in 0 until n) {

            // OR 연산
            val value = arr1[i] or arr2[i]
          
            // 이진수 변환
            var result = Integer.toBinaryString(value)
            
            // 앞쪽 0 채우기
            result = result.padStart(n, '0')

            // 문자열 변환
            result = result
                .replace('1', '#')
                .replace('0', ' ')

            answer.add(result)
        }

        return answer.toTypedArray()
    }
}
