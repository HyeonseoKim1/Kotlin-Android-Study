// Summer/Winter Coding(~2018) > 방문 길이 (코틀린 풀이)
// 좌표가 아니라 길 자체를 저장하기

class Solution {
    fun solution(dirs: String): Int {
        var answer = 0

        var x = 0
        var y = 0

        val visited = mutableSetOf<String>()

        val move = mapOf(
            'U' to Pair(0, 1),
            'D' to Pair(0, -1),
            'R' to Pair(1, 0),
            'L' to Pair(-1, 0)
        )

        for (d in dirs) {

            val (dx, dy) = move[d]!!

            val nx = x + dx
            val ny = y + dy

            // 범위를 벗어나는 경우
            if (nx !in -5..5 || ny !in -5..5) {
                continue
            }

            val path = "$x,$y,$nx,$ny"
            val reversePath = "$nx,$ny,$x,$y"

            // 처음 지나가는 길인 경우
            if (path !in visited) {
                visited.add(path)
                visited.add(reversePath)
                answer++
            }

            x = nx
            y = ny
        }

        return answer
    }
}
