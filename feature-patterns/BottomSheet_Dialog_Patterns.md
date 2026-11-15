# Bottom Sheet / Dialog 패턴: 필터, 정렬, 선택 시트

상품 목록 화면에 정렬, 필터, 브랜드 선택을 붙이는 시나리오로 Compose Material3의 `ModalBottomSheet`과 `AlertDialog` 패턴을 정리한다. MVI + Hilt 기준이다.

1. Bottom Sheet와 Dialog 선택 기준
2. 의존성
3. 도메인 모델 (Product, Filter, Sort)
4. MVI 계약 (State / Intent) 과 Reducer
5. 공통 Bottom Sheet 래퍼
6. 정렬 시트 (단일 선택)
7. 선택 시트 (다중 선택 + 확인)
8. 필터 시트 (임시 상태 + 적용)
9. Dialog 패턴 (확인 / 커스텀)
10. 화면 조립
11. 즉시 적용 vs 적용 버튼
12. 엣지 케이스와 주의점
13. 테스트
14. 정리

<br>

# 1. Bottom Sheet와 Dialog 선택 기준

| 구분 | 쓰는 경우 | 예시 |
|---|---|---|
| Modal Bottom Sheet | 목록 선택, 여러 입력을 모아서 적용, 터치하기 쉬운 하단 배치가 중요할 때 | 정렬, 필터, 공유 메뉴, 옵션 선택 |
| Standard Bottom Sheet (`BottomSheetScaffold`) | 본문과 함께 계속 떠 있는 보조 패널 | 지도 위 장소 목록, 음악 플레이어 |
| AlertDialog | 짧은 확인, 되돌릴 수 없는 작업 경고, 한두 개 선택지 | 삭제 확인, 초기화 확인, 권한 안내 |
| 커스텀 Dialog | 중앙 집중이 필요한 짧은 입력 | 이름 변경, 평점 입력 |
| DropdownMenu | 앵커 바로 아래 가벼운 메뉴 | 더보기 메뉴 |

판단 기준은 이렇게 잡는다.

- 선택지가 많거나 입력 요소가 여러 개면 Bottom Sheet다. 엄지 닿는 위치에 있고 내용을 스크롤할 수 있다.
- 사용자에게 "정말 하시겠습니까?"를 묻는 중단형 확인은 Dialog다.
- 한 손가락으로 끝나는 짧은 선택은 시트, 흐름을 멈추고 확인받아야 하는 것은 Dialog로 나눈다.

## 1.1 이 노트에서 만들 화면

```text
[ 정렬: 최신순 ▾ ] [ 필터 (2) ] [ 브랜드 ▾ ]     <- 상단 칩 줄
─────────────────────────────────
상품 목록 (정렬 + 필터 적용된 결과)

칩 클릭 시
 ├─ 정렬   -> SingleSelectSheet  (선택 즉시 적용, 시트 닫힘)
 ├─ 필터   -> FilterSheet        (카테고리/가격/재고, 적용 버튼)
 └─ 브랜드 -> MultiSelectSheet   (체크박스, 확인 버튼)

필터 초기화 -> AlertDialog 확인
```

<br>

# 2. 의존성

```kotlin
// build.gradle.kts (app)
dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.lifecycle:lifecycle-runtime-compose")
    implementation("androidx.hilt:hilt-navigation-compose")
}
```

`ModalBottomSheet`, `BasicAlertDialog`, `RangeSlider`은 버전에 따라 `@ExperimentalMaterial3Api` 옵트인이 필요하다. `FlowRow`는 `@ExperimentalLayoutApi`다.

<br>

# 3. 도메인 모델 (Product, Filter, Sort)

```kotlin
data class Product(
    val id: Long,
    val name: String,
    val brand: String,
    val category: Category,
    val price: Int,
    val popularity: Int,
    val inStock: Boolean,
)

enum class Category(val label: String) {
    TOP("상의"),
    BOTTOM("하의"),
    SHOES("신발"),
    BAG("가방"),
    ACC("액세서리"),
}

enum class SortOption(val label: String) {
    LATEST("최신순"),
    POPULAR("인기순"),
    PRICE_LOW("낮은 가격순"),
    PRICE_HIGH("높은 가격순"),
}
```

필터는 불변 data class로 만들고, 상품이 필터에 맞는지 판단하는 로직(`matches`)을 필터 자신이 가진다. 시트에서 "결과 N개 보기"를 미리 계산할 때도 같은 함수를 쓰기 위해서다.

```kotlin
const val PRICE_MAX = 100_000f
private val DEFAULT_PRICE_RANGE = 0f..PRICE_MAX

data class ProductFilter(
    val categories: Set<Category> = emptySet(),
    val brands: Set<String> = emptySet(),
    val priceRange: ClosedFloatingPointRange<Float> = DEFAULT_PRICE_RANGE,
    val onlyInStock: Boolean = false,
) {
    /** 상단 칩에 표시할 적용된 필터 개수 (브랜드는 별도 칩이므로 제외) */
    val activeCount: Int
        get() = listOf(
            categories.isNotEmpty(),
            priceRange != DEFAULT_PRICE_RANGE,
            onlyInStock,
        ).count { it }

    fun matches(product: Product): Boolean =
        (categories.isEmpty() || product.category in categories) &&
            (brands.isEmpty() || product.brand in brands) &&
            product.price.toFloat() in priceRange &&
            (!onlyInStock || product.inStock)
}

fun List<Product>.applyFilterAndSort(
    filter: ProductFilter,
    sort: SortOption,
): List<Product> {
    val filtered = filter { filter.matches(it) }
    return when (sort) {
        SortOption.LATEST -> filtered.sortedByDescending { it.id }
        SortOption.POPULAR -> filtered.sortedByDescending { it.popularity }
        SortOption.PRICE_LOW -> filtered.sortedBy { it.price }
        SortOption.PRICE_HIGH -> filtered.sortedByDescending { it.price }
    }
}
```

샘플 데이터는 서버 대신 메모리에서 만든다.

```kotlin
val SAMPLE_BRANDS = listOf("Nova", "Orbit", "Pixel", "Quark", "Rally", "Stride")

object SampleProducts {
    val all: List<Product> = List(40) { i ->
        Product(
            id = i.toLong(),
            name = "상품 ${i + 1}",
            brand = SAMPLE_BRANDS[i % SAMPLE_BRANDS.size],
            category = Category.entries[i % Category.entries.size],
            price = 5_000 + (i * 2_300) % 95_000,
            popularity = (i * 37) % 100,
            inStock = i % 4 != 0,
        )
    }
}
```

<br>

# 4. MVI 계약 (State / Intent) 과 Reducer

## 4.1 어떤 시트가 열려 있는지는 State에 둔다

```kotlin
enum class ProductSheet { SORT, FILTER, BRAND }

data class ProductListState(
    val products: List<Product> = emptyList(),
    val filter: ProductFilter = ProductFilter(),
    val sort: SortOption = SortOption.LATEST,
    val sheet: ProductSheet? = null,          // null이면 시트 없음
    val showResetDialog: Boolean = false,
)

sealed interface ProductListIntent {
    data class OpenSheet(val sheet: ProductSheet) : ProductListIntent
    data object DismissSheet : ProductListIntent

    data class SelectSort(val sort: SortOption) : ProductListIntent
    data class ApplyFilter(val filter: ProductFilter) : ProductListIntent
    data class ApplyBrands(val brands: Set<String>) : ProductListIntent

    data object RequestReset : ProductListIntent
    data object ConfirmReset : ProductListIntent
    data object DismissResetDialog : ProductListIntent
}
```

시트/다이얼로그의 열림 상태를 ViewModel State에 두는 이유는 다음과 같다.

- 화면 회전이나 프로세스 복구 후에도 열려 있던 시트가 그대로 복원된다.
- "시트 닫기 + 필터 적용"이 하나의 State 전이로 묶여서 중간 상태가 생기지 않는다.
- 테스트에서 Intent만 보내면 열림/닫힘까지 검증할 수 있다.

반대로 순수하게 UI에만 영향을 주는 일회성 요소(툴팁, 단순 드롭다운)는 로컬 `rememberSaveable`로 충분하다. 필터처럼 비즈니스 State와 엮이면 State에 둔다.

## 4.2 Reducer를 순수 함수로 분리

상태 전이를 순수 함수로 빼두면 ViewModel이 얇아지고 코루틴 없이 바로 테스트된다.

```kotlin
fun reduce(state: ProductListState, intent: ProductListIntent): ProductListState =
    when (intent) {
        is ProductListIntent.OpenSheet -> state.copy(sheet = intent.sheet)
        ProductListIntent.DismissSheet -> state.copy(sheet = null)

        is ProductListIntent.SelectSort -> state.copy(sort = intent.sort, sheet = null)
        is ProductListIntent.ApplyFilter -> state.copy(filter = intent.filter, sheet = null)
        is ProductListIntent.ApplyBrands -> state.copy(
            filter = state.filter.copy(brands = intent.brands),
            sheet = null,
        )

        ProductListIntent.RequestReset -> state.copy(showResetDialog = true)
        ProductListIntent.ConfirmReset -> state.copy(
            filter = ProductFilter(),
            sort = SortOption.LATEST,
            showResetDialog = false,
        )
        ProductListIntent.DismissResetDialog -> state.copy(showResetDialog = false)
    }
```

## 4.3 ViewModel

```kotlin
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class ProductListViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(
        ProductListState(products = SampleProducts.all)
    )
    val state: StateFlow<ProductListState> = _state.asStateFlow()

    fun onIntent(intent: ProductListIntent) {
        _state.update { reduce(it, intent) }
    }
}
```

실제 앱에서는 `products`를 Repository에서 받아오고, 필터/정렬이 서버 쿼리라면 `ApplyFilter` 처리에서 재요청을 날리는 형태로 확장한다. 시트 패턴 자체는 달라지지 않는다.

<br>

# 5. 공통 Bottom Sheet 래퍼

## 5.1 문제: 상태만 null로 바꾸면 닫힘 애니메이션이 사라진다

```kotlin
// 이렇게 쓰면 sheet가 null이 되는 순간 컴포지션에서 즉시 사라진다
if (state.sheet != null) {
    ModalBottomSheet(onDismissRequest = { ... }) { ... }
}
```

사용자가 스크림을 터치하거나 아래로 스와이프하면 `ModalBottomSheet`가 알아서 내려가는 애니메이션을 끝낸 뒤 `onDismissRequest`를 호출한다. 그런데 시트 안의 "적용" 버튼에서 곧바로 State를 바꾸면 애니메이션 없이 뚝 끊긴다.

해결책은 `sheetState.hide()`로 애니메이션을 먼저 재생하고, 끝난 다음에 State를 바꾸는 것이다.

## 5.2 래퍼 구현

```kotlin
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.(hideThen: (() -> Unit) -> Unit) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = skipPartiallyExpanded,
    )
    val scope = rememberCoroutineScope()

    // 닫힘 애니메이션을 재생한 뒤 afterHidden을 실행
    val hideThen: (() -> Unit) -> Unit = { afterHidden ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) afterHidden()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        content(hideThen)
    }
}
```

사용법은 단순하다.

- 시트 안에서 State를 바꾸는 모든 동작은 `hideThen { onIntent(...) }`로 감싼다.
- 바깥 터치/스와이프/뒤로가기는 래퍼가 `onDismissRequest`로 이미 처리한다.
- `invokeOnCompletion`에서 `isVisible`을 확인하는 이유는 `hide()` 도중 코루틴이 취소된 경우(시트가 다시 열린 경우)에 `afterHidden`이 잘못 실행되는 것을 막기 위해서다.
- `skipPartiallyExpanded = true`는 반쯤 열린 중간 상태를 없애고 내용 높이만큼 열리게 한다. 필터처럼 내용이 정해진 시트는 대부분 이 설정이 낫다.

## 5.3 시트 제목 공통 컴포넌트

```kotlin
@Composable
fun SheetTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    )
}
```

<br>

# 6. 정렬 시트 (단일 선택)

정렬은 "하나 고르면 바로 적용되고 닫히는" 가장 단순한 선택 시트다. 정렬뿐 아니라 지역, 언어, 배송 방법 등에 재사용할 수 있게 제네릭으로 만든다.

```kotlin
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun <T> SingleSelectSheet(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AppModalBottomSheet(onDismissRequest = onDismissRequest) { hideThen ->
        SheetTitle(title)

        options.forEach { option ->
            val isSelected = option == selected

            ListItem(
                headlineContent = { Text(optionLabel(option)) },
                leadingContent = {
                    // 클릭은 Row 전체의 selectable이 처리하므로 onClick = null
                    RadioButton(selected = isSelected, onClick = null)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.selectable(
                    selected = isSelected,
                    role = Role.RadioButton,
                    onClick = { hideThen { onSelect(option) } },
                ),
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}
```

- `RadioButton(onClick = null)`로 두고 `selectable`을 행 전체에 걸어서 터치 영역을 넓힌다. 접근성 서비스에서도 행 전체가 하나의 라디오 항목으로 읽힌다.
- 같은 값을 다시 눌러도 `onSelect`가 호출되는데, Reducer가 같은 값으로 `copy`하므로 문제없다.

호출부는 이렇게 된다.

```kotlin
SingleSelectSheet(
    title = "정렬",
    options = SortOption.entries,
    selected = state.sort,
    optionLabel = { it.label },
    onSelect = { onIntent(ProductListIntent.SelectSort(it)) },
    onDismissRequest = { onIntent(ProductListIntent.DismissSheet) },
)
```

<br>

# 7. 선택 시트 (다중 선택 + 확인)

브랜드처럼 항목이 많고 여러 개를 고르는 경우다. 체크할 때마다 바로 적용하면 목록이 계속 바뀌어 산만하므로, 시트 안에서는 임시 선택(draft)만 바꾸고 "확인"에서 한 번에 적용한다.

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun <T> MultiSelectSheet(
    title: String,
    options: List<T>,
    selected: Set<T>,
    optionLabel: (T) -> String,
    onConfirm: (Set<T>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    // 시트가 열릴 때의 선택값으로 draft 초기화
    var draft by remember { mutableStateOf(selected) }

    AppModalBottomSheet(onDismissRequest = onDismissRequest) { hideThen ->
        SheetTitle(title)

        // 항목이 많아도 시트 높이를 넘지 않도록 남는 공간 안에서만 스크롤
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(options) { option ->
                val checked = option in draft

                ListItem(
                    headlineContent = { Text(optionLabel(option)) },
                    leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.toggleable(
                        value = checked,
                        role = Role.Checkbox,
                        onValueChange = { isChecked ->
                            draft = if (isChecked) draft + option else draft - option
                        },
                    ),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { draft = emptySet() },
                modifier = Modifier.weight(1f),
            ) {
                Text("전체 해제")
            }
            Button(
                onClick = { hideThen { onConfirm(draft) } },
                modifier = Modifier.weight(2f),
            ) {
                Text(if (draft.isEmpty()) "확인" else "${draft.size}개 선택 완료")
            }
        }
    }
}
```

필요한 추가 import: `androidx.compose.foundation.selection.toggleable`.

- `Modifier.weight(1f, fill = false)`는 내용이 짧으면 내용 높이만큼, 길면 시트 최대 높이 안에서 스크롤되게 한다. `LazyColumn`을 시트 안에 바로 두면 높이가 무한대로 계산되는 오류가 날 수 있어서 weight가 필요하다.
- draft는 시트가 닫히면 사라진다. 바깥을 눌러 닫으면 변경은 폐기되고, "확인"을 눌러야만 반영된다.

호출부:

```kotlin
MultiSelectSheet(
    title = "브랜드",
    options = SAMPLE_BRANDS,
    selected = state.filter.brands,
    optionLabel = { it },
    onConfirm = { onIntent(ProductListIntent.ApplyBrands(it)) },
    onDismissRequest = { onIntent(ProductListIntent.DismissSheet) },
)
```

<br>

# 8. 필터 시트 (임시 상태 + 적용)

필터 시트는 요소가 여러 개(카테고리 칩, 가격 슬라이더, 재고 스위치)라서 "임시 필터(draft)를 만들고, 적용 버튼에서 확정"하는 구조가 핵심이다.

## 8.1 설계 포인트

- 시트 안의 모든 조작은 로컬 `draft: ProductFilter`만 바꾼다. 바깥(ViewModel)의 `filter`는 건드리지 않는다.
- "N개 보기" 버튼에서 draft로 필터링했을 때의 결과 개수를 미리 보여준다. 결과가 0개인 조합을 사용자가 적용하기 전에 알 수 있다.
- "초기화"는 draft만 기본값으로 되돌린다(시트 안에서). 앱 전체 필터를 지우는 초기화는 상단에서 Dialog로 확인받는다.

## 8.2 구현

```kotlin
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    applied: ProductFilter,
    resultCount: (ProductFilter) -> Int,
    onApply: (ProductFilter) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var draft by remember { mutableStateOf(applied) }

    AppModalBottomSheet(onDismissRequest = onDismissRequest) { hideThen ->
        SheetTitle("필터")

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 1) 카테고리: 다중 선택 칩
            FilterSection(title = "카테고리") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Category.entries.forEach { category ->
                        val selected = category in draft.categories

                        FilterChip(
                            selected = selected,
                            onClick = {
                                draft = draft.copy(
                                    categories = if (selected) {
                                        draft.categories - category
                                    } else {
                                        draft.categories + category
                                    },
                                )
                            },
                            label = { Text(category.label) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            // 2) 가격 범위
            FilterSection(
                title = "가격",
                trailing = "${draft.priceRange.start.toInt()}원 ~ ${draft.priceRange.endInclusive.toInt()}원",
            ) {
                RangeSlider(
                    value = draft.priceRange,
                    onValueChange = { draft = draft.copy(priceRange = it) },
                    valueRange = 0f..PRICE_MAX,
                    steps = 9, // 10,000원 단위 (양 끝 제외 9칸)
                )
            }

            // 3) 재고
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("재고 있는 상품만", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = draft.onlyInStock,
                    onCheckedChange = { draft = draft.copy(onlyInStock = it) },
                )
            }
        }

        // 하단 고정 버튼
        val count = remember(draft) { resultCount(draft) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { draft = ProductFilter(brands = draft.brands) },
                modifier = Modifier.weight(1f),
            ) {
                Text("초기화")
            }
            Button(
                onClick = { hideThen { onApply(draft) } },
                enabled = count > 0,
                modifier = Modifier.weight(2f),
            ) {
                Text(if (count > 0) "${count}개 상품 보기" else "결과 없음")
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    trailing: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
        content()
    }
}
```

포인트 정리:

- 필터 시트의 `draft`에서 `brands`는 건드리지 않는다. 브랜드는 별도 시트가 관리하므로, 필터 시트의 초기화가 브랜드까지 날리지 않게 `ProductFilter(brands = draft.brands)`로 유지한다.
- `resultCount`는 람다로 받는다. 시트는 상품 목록을 몰라도 되고, 호출부에서 `products.count { filter.matches(it) }`를 넘겨준다.
- 결과가 0개면 적용 버튼을 비활성화한다. 빈 화면을 만드는 조합을 미리 막는 UX다.
- 시트 안 본문은 `verticalScroll` + `weight(1f, fill = false)`로 감싼다. 작은 화면이나 글씨 크기를 키운 경우에도 하단 버튼이 화면 밖으로 밀리지 않는다.

<br>

# 9. Dialog 패턴 (확인 / 커스텀)

## 9.1 확인 다이얼로그

필터 전체 초기화처럼 "여러 설정이 한꺼번에 사라지는" 동작은 Dialog로 한 번 확인받는다.

```kotlin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

@Composable
fun ConfirmResetDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("필터를 초기화할까요?") },
        text = { Text("적용된 정렬과 필터가 모두 기본값으로 돌아갑니다.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("초기화") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}
```

- `onDismissRequest`는 바깥 터치와 뒤로가기 모두에서 호출된다. 취소 버튼과 같은 동작으로 연결한다.
- Dialog는 시트와 달리 닫힘 애니메이션을 직접 제어할 필요가 없다. State를 `false`로 바꾸면 라이브러리가 알아서 처리한다.
- 확인 버튼 라벨은 "확인"보다 동작을 그대로 쓴다("초기화", "삭제"). 어떤 결과가 나올지 눈에 바로 들어온다.

## 9.2 다이얼로그 상태를 데이터와 함께 들고 싶을 때

삭제 확인처럼 "무엇을"이 필요한 경우에는 Boolean 대신 대상 데이터를 가진 sealed 타입을 쓴다.

```kotlin
sealed interface ProductDialog {
    data class ConfirmDelete(val productId: Long) : ProductDialog
    data object ConfirmReset : ProductDialog
}

// State
data class SomeState(val dialog: ProductDialog? = null)

// Compose
when (val dialog = state.dialog) {
    is ProductDialog.ConfirmDelete -> DeleteDialog(
        onConfirm = { onIntent(Intent.Delete(dialog.productId)) },
        onDismiss = { onIntent(Intent.DismissDialog) },
    )
    ProductDialog.ConfirmReset -> ConfirmResetDialog(/* ... */)
    null -> Unit
}
```

다이얼로그 종류가 둘 이상 생기거나 데이터가 필요해지면 이 구조로 올린다. 하나뿐이고 데이터가 없으면 Boolean이 더 단순하다.

## 9.3 커스텀 Dialog

내용을 직접 구성해야 할 때는 `BasicAlertDialog`에 `Surface`를 얹는다.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("이름 변경", style = MaterialTheme.typography.titleLarge)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("취소") }
                    TextButton(
                        onClick = { onConfirm(name.trim()) },
                        enabled = name.isNotBlank(),
                    ) {
                        Text("저장")
                    }
                }
            }
        }
    }
}
```

필요한 추가 import: `androidx.compose.material3.BasicAlertDialog`, `androidx.compose.material3.Surface`, `androidx.compose.material3.OutlinedTextField`.

<br>

# 10. 화면 조립

## 10.1 상단 칩 줄

```kotlin
import androidx.compose.material.icons.filled.ArrowDropDown

@Composable
fun ProductFilterBar(
    sort: SortOption,
    filter: ProductFilter,
    onOpenSheet: (ProductSheet) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = sort != SortOption.LATEST,
            onClick = { onOpenSheet(ProductSheet.SORT) },
            label = { Text(sort.label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
        )

        FilterChip(
            selected = filter.activeCount > 0,
            onClick = { onOpenSheet(ProductSheet.FILTER) },
            label = {
                Text(if (filter.activeCount > 0) "필터 ${filter.activeCount}" else "필터")
            },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
        )

        FilterChip(
            selected = filter.brands.isNotEmpty(),
            onClick = { onOpenSheet(ProductSheet.BRAND) },
            label = {
                Text(if (filter.brands.isNotEmpty()) "브랜드 ${filter.brands.size}" else "브랜드")
            },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
        )
    }
}
```

필요한 추가 import: `androidx.compose.foundation.horizontalScroll`.

적용된 상태를 칩의 `selected`와 개수 표시로 보여주면, 시트를 열지 않아도 지금 필터가 걸려 있는지 알 수 있다.

## 10.2 Route와 Screen

```kotlin
@Composable
fun ProductListRoute(
    viewModel: ProductListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ProductListScreen(
        state = state,
        onIntent = viewModel::onIntent,
    )
}
```

```kotlin
@Composable
fun ProductListScreen(
    state: ProductListState,
    onIntent: (ProductListIntent) -> Unit,
) {
    // 필터/정렬 결과는 입력이 바뀔 때만 다시 계산
    val visibleProducts = remember(state.products, state.filter, state.sort) {
        state.products.applyFilterAndSort(state.filter, state.sort)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("상품") },
                actions = {
                    val hasAnything = state.filter != ProductFilter() || state.sort != SortOption.LATEST
                    TextButton(
                        onClick = { onIntent(ProductListIntent.RequestReset) },
                        enabled = hasAnything,
                    ) {
                        Text("초기화")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            ProductFilterBar(
                sort = state.sort,
                filter = state.filter,
                onOpenSheet = { onIntent(ProductListIntent.OpenSheet(it)) },
            )

            if (visibleProducts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("조건에 맞는 상품이 없어요")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(visibleProducts, key = { it.id }) { product ->
                        ProductRow(product)
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    // 시트
    when (state.sheet) {
        ProductSheet.SORT -> SingleSelectSheet(
            title = "정렬",
            options = SortOption.entries,
            selected = state.sort,
            optionLabel = { it.label },
            onSelect = { onIntent(ProductListIntent.SelectSort(it)) },
            onDismissRequest = { onIntent(ProductListIntent.DismissSheet) },
        )

        ProductSheet.FILTER -> FilterSheet(
            applied = state.filter,
            resultCount = { draft -> state.products.count { draft.matches(it) } },
            onApply = { onIntent(ProductListIntent.ApplyFilter(it)) },
            onDismissRequest = { onIntent(ProductListIntent.DismissSheet) },
        )

        ProductSheet.BRAND -> MultiSelectSheet(
            title = "브랜드",
            options = SAMPLE_BRANDS,
            selected = state.filter.brands,
            optionLabel = { it },
            onConfirm = { onIntent(ProductListIntent.ApplyBrands(it)) },
            onDismissRequest = { onIntent(ProductListIntent.DismissSheet) },
        )

        null -> Unit
    }

    // 다이얼로그
    if (state.showResetDialog) {
        ConfirmResetDialog(
            onConfirm = { onIntent(ProductListIntent.ConfirmReset) },
            onDismiss = { onIntent(ProductListIntent.DismissResetDialog) },
        )
    }
}
```

```kotlin
@Composable
fun ProductRow(
    product: Product,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier,
        headlineContent = { Text(product.name) },
        supportingContent = {
            Text("${product.brand} · ${product.category.label}")
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text("%,d원".format(product.price))
                if (!product.inStock) {
                    Text(
                        text = "품절",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}
```

## 10.3 동작 흐름

1. 정렬 칩 탭 -> `OpenSheet(SORT)` -> `SingleSelectSheet` 표시
2. 항목 선택 -> 시트가 내려가는 애니메이션 재생 -> 끝나면 `SelectSort` -> 목록 재정렬
3. 필터 칩 탭 -> `FilterSheet` 표시 -> draft 조작 -> "N개 상품 보기" -> 닫히며 `ApplyFilter`
4. 필터 시트에서 바깥을 눌러 닫기 -> draft 폐기, 적용된 필터는 그대로
5. 상단 초기화 -> `AlertDialog` -> "초기화" 확인 시 필터와 정렬이 기본값으로 복귀

<br>

# 11. 즉시 적용 vs 적용 버튼

| 방식 | 장점 | 단점 | 어울리는 경우 |
|---|---|---|---|
| 즉시 적용 + 시트 닫힘 | 탭 한 번으로 끝, 구현 단순 | 실수로 눌러도 바로 반영 | 단일 선택 (정렬, 언어) |
| 즉시 적용 + 시트 유지 | 결과 변화를 실시간으로 확인 | 서버 조회면 요청이 잦아짐, 목록이 계속 변해 산만 | 필터 항목이 적고 로컬 필터링일 때 |
| draft + 적용 버튼 | 여러 항목을 조합한 뒤 한 번에 반영, 취소 가능 | draft 상태 관리 필요 | 다중 선택, 복합 필터 |
| draft + 결과 개수 미리보기 | 빈 결과를 사전에 방지 | 개수 계산 비용 | 쇼핑, 숙소 검색 등 |

이 노트에서는 정렬은 즉시 적용, 필터와 브랜드는 draft + 적용으로 나눴다. 기준은 "선택 한 번으로 결과가 명확한가"다.

<br>

# 12. 엣지 케이스와 주의점

## 12.1 시트 닫힘 애니메이션

- 시트 안의 버튼에서 State를 바로 바꾸면 애니메이션 없이 사라진다. 반드시 `hideThen`을 거친다.
- 바깥 터치, 스와이프, 뒤로가기는 `ModalBottomSheet`가 직접 처리하므로 `onDismissRequest`만 연결하면 된다.

## 12.2 draft 상태와 화면 회전

`var draft by remember { mutableStateOf(...) }`는 회전하면 초기화된다. 열린 시트 자체는 State에 있어서 복원되지만, 시트 안에서 만지던 값은 사라진다.

| 방법 | 설명 |
|---|---|
| 그냥 둔다 | 필터 정도는 다시 만져도 부담이 적다. 가장 단순 |
| `rememberSaveable` + Saver | `ProductFilter`를 저장할 Saver를 작성 (Set, Range 직렬화 필요) |
| ViewModel State에 draft 포함 | `OpenFilter` 시 draft를 복사해 State에 두고, 시트 내 조작을 Intent로 처리. 가장 확실하지만 Intent가 늘어남 |

입력 비용이 큰 시트(긴 폼, 텍스트 입력)라면 세 번째 방식을 쓴다.

## 12.3 키보드와 시트

시트 안에 `TextField`(브랜드 검색 등)를 넣으면 키보드가 시트를 가린다.

- 시트 content에 `Modifier.imePadding()`을 준다.
- `skipPartiallyExpanded = true`로 시트가 전체 높이를 쓸 수 있게 한다.
- 키보드가 올라온 상태에서 "확인"을 눌러 닫을 때 포커스를 해제(`LocalFocusManager.current.clearFocus()`)하지 않으면 닫힘 애니메이션 중 키보드가 깜빡일 수 있다.

## 12.4 시트 안에서 스크롤

- 시트 content는 기본적으로 스크롤되지 않는다. 긴 내용은 `verticalScroll` 또는 `LazyColumn`을 직접 넣어야 한다.
- 시트 안에 스크롤 컨테이너를 두면 "시트 내려 닫기"와 "내용 스크롤" 제스처가 겹친다. 내용이 맨 위일 때만 시트 드래그가 동작하도록 Material3가 처리해준다. `weight(1f, fill = false)`는 이 동작을 깨지 않는다.

## 12.5 시트 중첩 금지

필터 시트 위에 브랜드 시트를 또 올리는 식의 중첩은 피한다. 뒤로가기 처리가 꼬이고 시각적으로도 혼란스럽다. 대안은 다음과 같다.

- 한 시트 안에서 화면을 교체한다(내부 상태로 "필터 메인 / 브랜드 선택" 전환).
- 이 노트처럼 시트를 분리하고 각각 독립적으로 연다.
- 선택 항목이 많으면 시트 대신 전체 화면 라우트로 올린다.

## 12.6 WindowInsets와 edge-to-edge

- Material3 `ModalBottomSheet`는 기본적으로 내비게이션 바 영역을 고려한 insets를 적용한다. 하단 버튼이 제스처 바에 가려진다면 `contentWindowInsets` 설정을 확인한다.
- 시트 마지막에 `Spacer(Modifier.height(16.dp))` 정도의 여백을 둬서 마지막 항목이 화면 끝에 붙지 않게 한다.

## 12.7 태블릿과 큰 화면

`ModalBottomSheet`는 큰 화면에서 폭이 `sheetMaxWidth`(기본 640dp)로 제한되어 중앙에 뜬다. 필요하면 `sheetMaxWidth` 파라미터로 조정한다.

## 12.8 접근성

- 시트 제목은 `Modifier.semantics { heading() }`를 주면 TalkBack에서 제목으로 읽힌다.
- 라디오/체크 행은 `selectable`/`toggleable`로 행 전체에 역할(Role)을 부여한다.
- 칩의 선택 상태는 `selected`로 전달되므로 별도 설명이 필요 없다. 다만 "필터 2" 같은 텍스트는 "필터 2개 적용됨"처럼 `contentDescription`을 주면 더 명확하다.
- 터치 영역은 최소 48dp를 유지한다(`ListItem`, `FilterChip`은 기본 충족).

## 12.9 흔한 실수

- 시트 내부에서 State를 바로 바꿔서 닫힘 애니메이션이 사라진다.
- 필터 시트가 ViewModel의 `filter`를 직접 수정해서 "취소"가 불가능해진다.
- `LazyColumn`을 `weight` 없이 시트 안에 넣어서 높이 제약 오류가 난다.
- 필터 결과 계산을 컴포지션마다 반복한다. `remember(products, filter, sort)`로 감싼다.
- 시트 열림 상태를 `remember`로만 들고 있어서 회전하면 시트가 닫힌다.
- Dialog를 Boolean 여러 개(`showA`, `showB`, ...)로 늘려 가다가 동시에 두 개가 열리는 상태가 가능해진다. 둘 이상이면 sealed 하나로 합친다.
- 확인 버튼 라벨이 모두 "확인"이라 뭘 하는 건지 불명확하다.

<br>

# 13. 테스트

## 13.1 Reducer 단위 테스트

Reducer가 순수 함수라서 코루틴, Dispatcher 설정 없이 바로 검증된다.

```kotlin
class ProductListReducerTest {

    private val initial = ProductListState(products = SampleProducts.all)

    @Test
    fun `OpenSheet는 해당 시트를 열고 DismissSheet는 닫는다`() {
        val opened = reduce(initial, ProductListIntent.OpenSheet(ProductSheet.FILTER))
        assertEquals(ProductSheet.FILTER, opened.sheet)

        val closed = reduce(opened, ProductListIntent.DismissSheet)
        assertNull(closed.sheet)
    }

    @Test
    fun `정렬 선택하면 정렬이 바뀌고 시트가 닫힌다`() {
        val opened = reduce(initial, ProductListIntent.OpenSheet(ProductSheet.SORT))
        val result = reduce(opened, ProductListIntent.SelectSort(SortOption.PRICE_LOW))

        assertEquals(SortOption.PRICE_LOW, result.sort)
        assertNull(result.sheet)
    }

    @Test
    fun `브랜드 적용은 다른 필터 값을 유지한다`() {
        val withFilter = initial.copy(filter = ProductFilter(onlyInStock = true))

        val result = reduce(withFilter, ProductListIntent.ApplyBrands(setOf("Nova")))

        assertTrue(result.filter.onlyInStock)
        assertEquals(setOf("Nova"), result.filter.brands)
    }

    @Test
    fun `초기화 확인하면 필터와 정렬이 기본값으로 돌아간다`() {
        val modified = initial.copy(
            filter = ProductFilter(categories = setOf(Category.SHOES)),
            sort = SortOption.POPULAR,
            showResetDialog = true,
        )

        val result = reduce(modified, ProductListIntent.ConfirmReset)

        assertEquals(ProductFilter(), result.filter)
        assertEquals(SortOption.LATEST, result.sort)
        assertFalse(result.showResetDialog)
    }
}
```

## 13.2 필터/정렬 로직 테스트

```kotlin
class ProductFilterTest {

    private val products = SampleProducts.all

    @Test
    fun `카테고리 필터는 선택한 카테고리만 남긴다`() {
        val filter = ProductFilter(categories = setOf(Category.SHOES))

        val result = products.applyFilterAndSort(filter, SortOption.LATEST)

        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.category == Category.SHOES })
    }

    @Test
    fun `재고 필터는 품절 상품을 제외한다`() {
        val result = products.applyFilterAndSort(
            ProductFilter(onlyInStock = true),
            SortOption.LATEST,
        )

        assertTrue(result.all { it.inStock })
    }

    @Test
    fun `낮은 가격순은 가격 오름차순이다`() {
        val result = products.applyFilterAndSort(ProductFilter(), SortOption.PRICE_LOW)

        assertEquals(result.map { it.price }.sorted(), result.map { it.price })
    }

    @Test
    fun `activeCount는 브랜드를 제외한 적용 항목 수를 센다`() {
        val filter = ProductFilter(
            categories = setOf(Category.TOP),
            onlyInStock = true,
            brands = setOf("Nova"),
        )

        assertEquals(2, filter.activeCount)
    }
}
```

## 13.3 Compose UI 테스트

`PostListScreen`처럼 `ProductListScreen`도 State와 콜백만 받는 순수 컴포저블이므로, 시트 표시 여부는 State를 직접 넣어서 검증한다.

```kotlin
class ProductListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun 정렬_시트에서_항목을_선택하면_SelectSort_Intent가_전달된다() {
        val intents = mutableListOf<ProductListIntent>()

        composeRule.setContent {
            ProductListScreen(
                state = ProductListState(
                    products = SampleProducts.all,
                    sheet = ProductSheet.SORT,
                ),
                onIntent = { intents += it },
            )
        }

        composeRule.onNodeWithText("인기순").performClick()
        composeRule.waitForIdle()

        assertTrue(intents.contains(ProductListIntent.SelectSort(SortOption.POPULAR)))
    }

    @Test
    fun 초기화_다이얼로그가_State에_따라_표시된다() {
        composeRule.setContent {
            ProductListScreen(
                state = ProductListState(
                    products = SampleProducts.all,
                    showResetDialog = true,
                ),
                onIntent = {},
            )
        }

        composeRule.onNodeWithText("필터를 초기화할까요?").assertIsDisplayed()
    }
}
```

`hideThen`은 닫힘 애니메이션이 끝난 뒤에 Intent를 보내기 때문에 `waitForIdle()` 또는 `waitUntil`이 필요하다.

<br>

# 정리

- 선택지가 많거나 여러 입력을 모아 적용하는 것은 `ModalBottomSheet`, 되돌릴 수 없는 작업의 중단형 확인은 `AlertDialog`로 나눈다.
- 시트와 다이얼로그의 열림 상태는 ViewModel State에 두면 회전 복원과 테스트가 쉽다. 다이얼로그가 둘 이상이거나 데이터가 필요하면 sealed 타입으로 합친다.
- 시트 안에서 State를 바꿀 때는 `sheetState.hide()`로 닫힘 애니메이션을 먼저 재생하고 끝난 뒤에 Intent를 보낸다. 이를 `AppModalBottomSheet`의 `hideThen`으로 공통화한다.
- 단일 선택(정렬)은 선택 즉시 적용하고 닫는다. 다중 선택과 복합 필터는 시트 안의 draft를 조작하고 "적용" 버튼에서 한 번에 반영한다.
- 필터 시트에는 적용 전 결과 개수를 보여주고, 결과가 0개면 적용을 막아서 빈 화면을 예방한다.
- 선택 시트는 제네릭(`SingleSelectSheet<T>`, `MultiSelectSheet<T>`)으로 만들어 정렬, 브랜드, 지역 등에 재사용한다.
- 시트 안의 긴 목록은 `LazyColumn`에 `weight(1f, fill = false)`를 주고, 하단 버튼은 스크롤 밖에 고정한다.
- 시트 중첩은 피하고, 키보드가 필요한 시트는 `imePadding`과 `skipPartiallyExpanded = true`를 같이 쓴다.
- 상태 전이는 순수 함수 Reducer로 분리하면 ViewModel이 얇아지고 코루틴 없이 단위 테스트할 수 있다.
- 필터/정렬 결과는 `remember(products, filter, sort)`로 입력이 바뀔 때만 다시 계산한다.
