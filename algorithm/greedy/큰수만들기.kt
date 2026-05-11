// number에서 k 개의 수를 제거했을 때 만들 수 있는 수 중 가장 큰 숫자를 문자열 형태로 반환
// 앞자리 큰 수 가장 많이 남기기

class Solution {
    fun solution(number: String, k: Int): String {
        var answer = ""
        
        val nums = mutableListOf<Char>()   // stack 역할
        var remove = k                     // 남은 삭제 횟수
        
        for (c in number) {
            
            // 앞자리보다 크면 삭제
            while (nums.isNotEmpty() &&
                   nums.last() < c &&
                   remove > 0) {
                
                nums.removeAt(nums.size - 1)
                remove--
            }
            
            nums.add(c)
        }
        
        // 삭제 남은 경우 뒤에서 삭제
        repeat(remove) {
            nums.removeAt(nums.size - 1)
        }
        
        answer = nums.joinToString("")
        return answer
    }
}
