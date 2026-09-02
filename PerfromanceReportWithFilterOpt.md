# RefFilter Performance Report

Generated: 2026-09-09T08:51:27.944518Z  
Configuration: **14 scenarios**, **10 repetitions × 50 iterations** per scenario  

## Summary

Each row is one benchmark scenario. Latencies are averages over 10 independent repetitions; ± values are the population standard deviation.  
`cold` = tag cache invalidated before every filter call.

| Scenario | Cache | Opt avg (ms) | Opt σ (ms) | Legacy avg (ms) | Legacy σ (ms) | Speedup avg | Speedup σ |
|---|---|---:|---:|---:|---:|---:|---:|
| tagsNotAtBranchTips_broadAllow | cold | 6,894 | 3,699 | 17,921 | 3,472 | 2,83x | 0,44x |
| broad-allow / no tags | warm | 1,434 | 0,113 | 10,604 | 0,363 | 7,44x | 0,59x |
| broad-allow / tip tags | warm | 4,056 | 0,216 | 14,234 | 0,243 | 3,52x | 0,19x |
| broad-allow / non-tip tags (cold cache) | cold | 5,403 | 0,117 | 16,111 | 0,279 | 2,98x | 0,08x |
| 200-DENY / no tags | warm | 3,250 | 0,097 | 9,868 | 0,245 | 3,04x | 0,14x |
| 200-DENY / tip tags | warm | 6,675 | 0,095 | 13,994 | 0,292 | 2,10x | 0,06x |
| 200-DENY / non-tip tags (cold cache) | cold | 8,046 | 0,112 | 15,426 | 0,251 | 1,92x | 0,05x |
| manyRefsBlocked (100 blocked) | warm | 2,196 | 0,064 | 11,332 | 0,351 | 5,16x | 0,22x |
| withTagsReachableFromVisibleBranches | warm | 3,725 | 0,065 | 14,210 | 0,265 | 3,82x | 0,06x |
| perUserPatternWithMatchingBranches | warm | 11,216 | 0,443 | 11,022 | 0,257 | 0,98x | 0,05x |
| mostRefsVisible_oneBlocked | warm | 1,327 | 0,049 | 10,425 | 0,304 | 7,86x | 0,26x |
| tagsNotAtBranchTips_denyRules | cold | 8,656 | 0,160 | 15,832 | 0,431 | 1,83x | 0,07x |
| tagsNotAtBranchTips_denyRules (baseline: broad allow) | cold | 5,189 | 0,094 | 18,117 | 0,340 | 3,49x | 0,09x |
| allRefsVisible_fastPathFires | warm | 0,101 | 0,046 | 0,086 | 0,008 | 0,94x | 0,21x |

## Per-Scenario Detail

### tagsNotAtBranchTips_broadAllow

- Tag cache: **cold (invalidated per call)**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 17,933 | 28,107 | 1,57x |
| 2 | 6,716 | 17,960 | 2,67x |
| 3 | 5,631 | 16,962 | 3,01x |
| 4 | 5,667 | 17,925 | 3,16x |
| 5 | 5,826 | 17,435 | 2,99x |
| 6 | 5,502 | 16,243 | 2,95x |
| 7 | 5,407 | 15,806 | 2,92x |
| 8 | 5,412 | 16,343 | 3,02x |
| 9 | 5,440 | 16,036 | 2,95x |
| 10 | 5,405 | 16,394 | 3,03x |
| **avg±σ** | **6,894±3,699** | **17,921±3,472** | **2,83±0,44x** |

### broad-allow / no tags

- Tag cache: **warm**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 1,402 | 11,503 | 8,20x |
| 2 | 1,716 | 10,829 | 6,31x |
| 3 | 1,433 | 10,624 | 7,41x |
| 4 | 1,450 | 10,181 | 7,02x |
| 5 | 1,292 | 10,629 | 8,22x |
| 6 | 1,290 | 10,457 | 8,11x |
| 7 | 1,465 | 10,450 | 7,13x |
| 8 | 1,397 | 10,792 | 7,72x |
| 9 | 1,482 | 10,323 | 6,97x |
| 10 | 1,410 | 10,253 | 7,27x |
| **avg±σ** | **1,434±0,113** | **10,604±0,363** | **7,44±0,59x** |

### broad-allow / tip tags

- Tag cache: **warm**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 4,024 | 14,465 | 3,59x |
| 2 | 3,801 | 14,622 | 3,85x |
| 3 | 4,491 | 14,519 | 3,23x |
| 4 | 3,904 | 13,984 | 3,58x |
| 5 | 4,333 | 13,828 | 3,19x |
| 6 | 3,844 | 13,980 | 3,64x |
| 7 | 4,142 | 14,143 | 3,41x |
| 8 | 3,929 | 14,302 | 3,64x |
| 9 | 4,191 | 14,267 | 3,40x |
| 10 | 3,903 | 14,230 | 3,65x |
| **avg±σ** | **4,056±0,216** | **14,234±0,243** | **3,52±0,19x** |

### broad-allow / non-tip tags (cold cache)

- Tag cache: **cold (invalidated per call)**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 5,629 | 15,863 | 2,82x |
| 2 | 5,351 | 16,661 | 3,11x |
| 3 | 5,431 | 15,830 | 2,91x |
| 4 | 5,451 | 16,544 | 3,04x |
| 5 | 5,358 | 16,065 | 3,00x |
| 6 | 5,552 | 16,186 | 2,92x |
| 7 | 5,230 | 16,071 | 3,07x |
| 8 | 5,422 | 15,939 | 2,94x |
| 9 | 5,355 | 16,165 | 3,02x |
| 10 | 5,250 | 15,784 | 3,01x |
| **avg±σ** | **5,403±0,117** | **16,111±0,279** | **2,98±0,08x** |

### 200-DENY / no tags

- Tag cache: **warm**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 3,119 | 10,231 | 3,28x |
| 2 | 3,115 | 9,633 | 3,09x |
| 3 | 3,338 | 9,651 | 2,89x |
| 4 | 3,440 | 9,855 | 2,87x |
| 5 | 3,267 | 9,649 | 2,95x |
| 6 | 3,276 | 9,604 | 2,93x |
| 7 | 3,238 | 10,103 | 3,12x |
| 8 | 3,326 | 9,927 | 2,99x |
| 9 | 3,164 | 10,284 | 3,25x |
| 10 | 3,217 | 9,737 | 3,03x |
| **avg±σ** | **3,250±0,097** | **9,868±0,245** | **3,04±0,14x** |

### 200-DENY / tip tags

- Tag cache: **warm**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 6,812 | 14,118 | 2,07x |
| 2 | 6,600 | 14,093 | 2,14x |
| 3 | 6,517 | 14,590 | 2,24x |
| 4 | 6,682 | 13,705 | 2,05x |
| 5 | 6,706 | 14,132 | 2,11x |
| 6 | 6,821 | 13,793 | 2,02x |
| 7 | 6,753 | 13,662 | 2,02x |
| 8 | 6,579 | 13,689 | 2,08x |
| 9 | 6,634 | 13,838 | 2,09x |
| 10 | 6,651 | 14,317 | 2,15x |
| **avg±σ** | **6,675±0,095** | **13,994±0,292** | **2,10±0,06x** |

### 200-DENY / non-tip tags (cold cache)

- Tag cache: **cold (invalidated per call)**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 7,823 | 15,202 | 1,94x |
| 2 | 8,197 | 15,043 | 1,84x |
| 3 | 7,911 | 15,541 | 1,96x |
| 4 | 8,045 | 15,952 | 1,98x |
| 5 | 8,096 | 15,357 | 1,90x |
| 6 | 8,046 | 15,298 | 1,90x |
| 7 | 8,013 | 15,536 | 1,94x |
| 8 | 7,999 | 15,692 | 1,96x |
| 9 | 8,186 | 15,230 | 1,86x |
| 10 | 8,139 | 15,409 | 1,89x |
| **avg±σ** | **8,046±0,112** | **15,426±0,251** | **1,92±0,05x** |

### manyRefsBlocked (100 blocked)

- Tag cache: **warm**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 2,333 | 12,000 | 5,14x |
| 2 | 2,236 | 11,108 | 4,97x |
| 3 | 2,222 | 11,074 | 4,98x |
| 4 | 2,230 | 11,038 | 4,95x |
| 5 | 2,162 | 11,048 | 5,11x |
| 6 | 2,196 | 11,429 | 5,20x |
| 7 | 2,169 | 10,988 | 5,07x |
| 8 | 2,096 | 11,614 | 5,54x |
| 9 | 2,202 | 11,172 | 5,07x |
| 10 | 2,117 | 11,852 | 5,60x |
| **avg±σ** | **2,196±0,064** | **11,332±0,351** | **5,16±0,22x** |

### withTagsReachableFromVisibleBranches

- Tag cache: **warm**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 3,654 | 14,496 | 3,97x |
| 2 | 3,675 | 13,880 | 3,78x |
| 3 | 3,659 | 14,041 | 3,84x |
| 4 | 3,801 | 14,640 | 3,85x |
| 5 | 3,867 | 14,662 | 3,79x |
| 6 | 3,677 | 14,099 | 3,83x |
| 7 | 3,741 | 14,062 | 3,76x |
| 8 | 3,743 | 14,036 | 3,75x |
| 9 | 3,688 | 14,074 | 3,82x |
| 10 | 3,744 | 14,107 | 3,77x |
| **avg±σ** | **3,725±0,065** | **14,210±0,265** | **3,82±0,06x** |

### perUserPatternWithMatchingBranches

- Tag cache: **warm**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 12,246 | 10,742 | 0,88x |
| 2 | 11,324 | 11,450 | 1,01x |
| 3 | 10,656 | 10,763 | 1,01x |
| 4 | 10,846 | 11,367 | 1,05x |
| 5 | 11,047 | 10,991 | 0,99x |
| 6 | 11,417 | 10,700 | 0,94x |
| 7 | 11,174 | 11,308 | 1,01x |
| 8 | 10,960 | 11,052 | 1,01x |
| 9 | 11,632 | 10,872 | 0,93x |
| 10 | 10,855 | 10,973 | 1,01x |
| **avg±σ** | **11,216±0,443** | **11,022±0,257** | **0,98±0,05x** |

### mostRefsVisible_oneBlocked

- Tag cache: **warm**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 1,358 | 11,214 | 8,26x |
| 2 | 1,379 | 10,585 | 7,68x |
| 3 | 1,293 | 10,475 | 8,10x |
| 4 | 1,265 | 10,156 | 8,03x |
| 5 | 1,272 | 10,151 | 7,98x |
| 6 | 1,262 | 10,216 | 8,10x |
| 7 | 1,354 | 10,231 | 7,56x |
| 8 | 1,400 | 10,411 | 7,44x |
| 9 | 1,318 | 10,262 | 7,79x |
| 10 | 1,372 | 10,553 | 7,69x |
| **avg±σ** | **1,327±0,049** | **10,425±0,304** | **7,86±0,26x** |

### tagsNotAtBranchTips_denyRules

- Tag cache: **cold (invalidated per call)**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 8,615 | 15,927 | 1,85x |
| 2 | 8,547 | 15,313 | 1,79x |
| 3 | 8,624 | 15,350 | 1,78x |
| 4 | 8,662 | 15,778 | 1,82x |
| 5 | 8,384 | 15,796 | 1,88x |
| 6 | 8,833 | 15,330 | 1,74x |
| 7 | 8,642 | 16,562 | 1,92x |
| 8 | 8,790 | 16,110 | 1,83x |
| 9 | 8,960 | 15,655 | 1,75x |
| 10 | 8,499 | 16,504 | 1,94x |
| **avg±σ** | **8,656±0,160** | **15,832±0,431** | **1,83±0,07x** |

### tagsNotAtBranchTips_denyRules (baseline: broad allow)

- Tag cache: **cold (invalidated per call)**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 5,201 | 19,033 | 3,66x |
| 2 | 5,155 | 18,161 | 3,52x |
| 3 | 5,202 | 18,302 | 3,52x |
| 4 | 5,139 | 17,891 | 3,48x |
| 5 | 5,460 | 18,006 | 3,30x |
| 6 | 5,128 | 18,065 | 3,52x |
| 7 | 5,174 | 17,703 | 3,42x |
| 8 | 5,164 | 17,989 | 3,48x |
| 9 | 5,132 | 18,062 | 3,52x |
| 10 | 5,139 | 17,954 | 3,49x |
| **avg±σ** | **5,189±0,094** | **18,117±0,340** | **3,49±0,09x** |

### allRefsVisible_fastPathFires

- Tag cache: **warm**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 0,090 | 0,086 | 0,96x |
| 2 | 0,095 | 0,084 | 0,89x |
| 3 | 0,090 | 0,093 | 1,03x |
| 4 | 0,082 | 0,078 | 0,96x |
| 5 | 0,081 | 0,080 | 0,99x |
| 6 | 0,076 | 0,077 | 1,02x |
| 7 | 0,089 | 0,094 | 1,06x |
| 8 | 0,093 | 0,103 | 1,11x |
| 9 | 0,237 | 0,080 | 0,34x |
| 10 | 0,078 | 0,081 | 1,04x |
| **avg±σ** | **0,101±0,046** | **0,086±0,008** | **0,94±0,21x** |

