# RefFilter Performance Report

Generated: 2026-09-09T08:47:08.711693Z  
Configuration: **14 scenarios**, **10 repetitions × 50 iterations** per scenario  

## Summary

Each row is one benchmark scenario. Latencies are averages over 10 independent repetitions; ± values are the population standard deviation.  
`cold` = tag cache invalidated before every filter call.

| Scenario | Cache | Opt avg (ms) | Opt σ (ms) | Legacy avg (ms) | Legacy σ (ms) | Speedup avg | Speedup σ |
|---|---|---:|---:|---:|---:|---:|---:|
| tagsNotAtBranchTips_broadAllow | cold | 5,721 | 2,836 | 15,785 | 1,609 | 3,03x | 0,55x |
| broad-allow / no tags | warm | 1,416 | 0,094 | 10,193 | 0,393 | 7,22x | 0,44x |
| broad-allow / tip tags | warm | 3,153 | 0,127 | 13,389 | 0,167 | 4,25x | 0,18x |
| broad-allow / non-tip tags (cold cache) | cold | 4,611 | 0,118 | 14,951 | 0,117 | 3,24x | 0,10x |
| 200-DENY / no tags | warm | 3,033 | 0,074 | 9,349 | 0,120 | 3,08x | 0,10x |
| 200-DENY / tip tags | warm | 5,746 | 0,077 | 12,736 | 0,136 | 2,22x | 0,04x |
| 200-DENY / non-tip tags (cold cache) | cold | 7,161 | 0,122 | 14,157 | 0,197 | 1,98x | 0,05x |
| manyRefsBlocked (100 blocked) | warm | 2,152 | 0,143 | 11,023 | 0,349 | 5,14x | 0,37x |
| withTagsReachableFromVisibleBranches | warm | 3,030 | 0,058 | 13,103 | 0,263 | 4,33x | 0,13x |
| perUserPatternWithMatchingBranches | warm | 11,384 | 0,464 | 11,320 | 0,341 | 1,00x | 0,02x |
| mostRefsVisible_oneBlocked | warm | 1,332 | 0,061 | 10,508 | 0,403 | 7,91x | 0,47x |
| tagsNotAtBranchTips_denyRules | cold | 8,347 | 0,398 | 15,464 | 0,509 | 1,86x | 0,09x |
| tagsNotAtBranchTips_denyRules (baseline: broad allow) | cold | 4,631 | 0,505 | 17,165 | 0,809 | 3,73x | 0,25x |
| allRefsVisible_fastPathFires | warm | 0,094 | 0,014 | 0,093 | 0,021 | 1,00x | 0,17x |

## Per-Scenario Detail

### tagsNotAtBranchTips_broadAllow

- Tag cache: **cold (invalidated per call)**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 14,175 | 20,529 | 1,45x |
| 2 | 5,668 | 15,781 | 2,78x |
| 3 | 4,895 | 15,275 | 3,12x |
| 4 | 4,870 | 15,076 | 3,10x |
| 5 | 4,667 | 15,601 | 3,34x |
| 6 | 4,622 | 15,139 | 3,28x |
| 7 | 4,503 | 15,314 | 3,40x |
| 8 | 4,552 | 14,889 | 3,27x |
| 9 | 4,612 | 15,478 | 3,36x |
| 10 | 4,650 | 14,766 | 3,18x |
| **avg±σ** | **5,721±2,836** | **15,785±1,609** | **3,03±0,55x** |

### broad-allow / no tags

- Tag cache: **warm**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 1,563 | 11,213 | 7,17x |
| 2 | 1,445 | 10,019 | 6,93x |
| 3 | 1,338 | 10,078 | 7,53x |
| 4 | 1,581 | 9,921 | 6,27x |
| 5 | 1,306 | 9,864 | 7,55x |
| 6 | 1,287 | 10,080 | 7,83x |
| 7 | 1,372 | 10,434 | 7,60x |
| 8 | 1,414 | 10,030 | 7,09x |
| 9 | 1,443 | 9,851 | 6,83x |
| 10 | 1,415 | 10,439 | 7,38x |
| **avg±σ** | **1,416±0,094** | **10,193±0,393** | **7,22±0,44x** |

### broad-allow / tip tags

- Tag cache: **warm**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 3,106 | 13,511 | 4,35x |
| 2 | 3,047 | 13,769 | 4,52x |
| 3 | 3,123 | 13,408 | 4,29x |
| 4 | 3,201 | 13,324 | 4,16x |
| 5 | 3,177 | 13,411 | 4,22x |
| 6 | 3,040 | 13,223 | 4,35x |
| 7 | 3,487 | 13,350 | 3,83x |
| 8 | 3,146 | 13,131 | 4,17x |
| 9 | 3,018 | 13,472 | 4,46x |
| 10 | 3,183 | 13,288 | 4,17x |
| **avg±σ** | **3,153±0,127** | **13,389±0,167** | **4,25±0,18x** |

### broad-allow / non-tip tags (cold cache)

- Tag cache: **cold (invalidated per call)**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 4,590 | 15,098 | 3,29x |
| 2 | 4,494 | 14,996 | 3,34x |
| 3 | 4,578 | 14,949 | 3,27x |
| 4 | 4,511 | 14,963 | 3,32x |
| 5 | 4,881 | 14,880 | 3,05x |
| 6 | 4,454 | 15,091 | 3,39x |
| 7 | 4,687 | 14,931 | 3,19x |
| 8 | 4,579 | 14,753 | 3,22x |
| 9 | 4,711 | 14,772 | 3,14x |
| 10 | 4,629 | 15,076 | 3,26x |
| **avg±σ** | **4,611±0,118** | **14,951±0,117** | **3,24±0,10x** |

### 200-DENY / no tags

- Tag cache: **warm**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 2,948 | 9,648 | 3,27x |
| 2 | 2,980 | 9,317 | 3,13x |
| 3 | 3,086 | 9,362 | 3,03x |
| 4 | 3,040 | 9,184 | 3,02x |
| 5 | 3,091 | 9,276 | 3,00x |
| 6 | 3,028 | 9,372 | 3,10x |
| 7 | 2,973 | 9,376 | 3,15x |
| 8 | 3,090 | 9,227 | 2,99x |
| 9 | 3,175 | 9,407 | 2,96x |
| 10 | 2,926 | 9,320 | 3,19x |
| **avg±σ** | **3,033±0,074** | **9,349±0,120** | **3,08±0,10x** |

### 200-DENY / tip tags

- Tag cache: **warm**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 5,873 | 12,744 | 2,17x |
| 2 | 5,711 | 12,616 | 2,21x |
| 3 | 5,778 | 12,888 | 2,23x |
| 4 | 5,868 | 12,537 | 2,14x |
| 5 | 5,812 | 13,001 | 2,24x |
| 6 | 5,691 | 12,829 | 2,25x |
| 7 | 5,685 | 12,763 | 2,24x |
| 8 | 5,637 | 12,647 | 2,24x |
| 9 | 5,697 | 12,741 | 2,24x |
| 10 | 5,707 | 12,595 | 2,21x |
| **avg±σ** | **5,746±0,077** | **12,736±0,136** | **2,22±0,04x** |

### 200-DENY / non-tip tags (cold cache)

- Tag cache: **cold (invalidated per call)**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 7,137 | 14,183 | 1,99x |
| 2 | 7,165 | 14,463 | 2,02x |
| 3 | 7,337 | 13,718 | 1,87x |
| 4 | 6,975 | 14,235 | 2,04x |
| 5 | 7,099 | 13,962 | 1,97x |
| 6 | 7,183 | 14,300 | 1,99x |
| 7 | 7,173 | 14,074 | 1,96x |
| 8 | 7,377 | 14,118 | 1,91x |
| 9 | 7,183 | 14,196 | 1,98x |
| 10 | 6,984 | 14,317 | 2,05x |
| **avg±σ** | **7,161±0,122** | **14,157±0,197** | **1,98±0,05x** |

### manyRefsBlocked (100 blocked)

- Tag cache: **warm**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 2,200 | 11,870 | 5,40x |
| 2 | 2,178 | 10,850 | 4,98x |
| 3 | 2,168 | 10,844 | 5,00x |
| 4 | 2,050 | 11,252 | 5,49x |
| 5 | 2,031 | 10,790 | 5,31x |
| 6 | 2,091 | 10,740 | 5,14x |
| 7 | 2,543 | 10,753 | 4,23x |
| 8 | 2,152 | 10,702 | 4,97x |
| 9 | 2,029 | 11,260 | 5,55x |
| 10 | 2,080 | 11,168 | 5,37x |
| **avg±σ** | **2,152±0,143** | **11,023±0,349** | **5,14±0,37x** |

### withTagsReachableFromVisibleBranches

- Tag cache: **warm**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 3,011 | 13,696 | 4,55x |
| 2 | 2,988 | 13,027 | 4,36x |
| 3 | 2,983 | 12,805 | 4,29x |
| 4 | 3,146 | 12,831 | 4,08x |
| 5 | 3,011 | 12,896 | 4,28x |
| 6 | 3,051 | 13,307 | 4,36x |
| 7 | 2,994 | 13,047 | 4,36x |
| 8 | 2,971 | 13,328 | 4,49x |
| 9 | 3,131 | 12,932 | 4,13x |
| 10 | 3,015 | 13,158 | 4,36x |
| **avg±σ** | **3,030±0,058** | **13,103±0,263** | **4,33±0,13x** |

### perUserPatternWithMatchingBranches

- Tag cache: **warm**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 12,639 | 11,984 | 0,95x |
| 2 | 11,559 | 11,401 | 0,99x |
| 3 | 10,991 | 11,493 | 1,05x |
| 4 | 11,303 | 11,276 | 1,00x |
| 5 | 10,976 | 10,628 | 0,97x |
| 6 | 11,248 | 11,271 | 1,00x |
| 7 | 11,415 | 11,412 | 1,00x |
| 8 | 11,007 | 11,053 | 1,00x |
| 9 | 11,513 | 11,586 | 1,01x |
| 10 | 11,186 | 11,099 | 0,99x |
| **avg±σ** | **11,384±0,464** | **11,320±0,341** | **1,00±0,02x** |

### mostRefsVisible_oneBlocked

- Tag cache: **warm**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 1,279 | 11,457 | 8,96x |
| 2 | 1,353 | 10,715 | 7,92x |
| 3 | 1,380 | 10,358 | 7,51x |
| 4 | 1,233 | 10,219 | 8,29x |
| 5 | 1,314 | 10,102 | 7,69x |
| 6 | 1,352 | 10,043 | 7,43x |
| 7 | 1,234 | 10,282 | 8,33x |
| 8 | 1,369 | 10,883 | 7,95x |
| 9 | 1,403 | 10,521 | 7,50x |
| 10 | 1,402 | 10,499 | 7,49x |
| **avg±σ** | **1,332±0,061** | **10,508±0,403** | **7,91±0,47x** |

### tagsNotAtBranchTips_denyRules

- Tag cache: **cold (invalidated per call)**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 8,570 | 16,247 | 1,90x |
| 2 | 8,467 | 15,909 | 1,88x |
| 3 | 8,302 | 16,429 | 1,98x |
| 4 | 8,985 | 15,228 | 1,69x |
| 5 | 7,963 | 14,930 | 1,87x |
| 6 | 8,156 | 15,420 | 1,89x |
| 7 | 8,010 | 15,082 | 1,88x |
| 8 | 9,077 | 15,251 | 1,68x |
| 9 | 7,989 | 15,132 | 1,89x |
| 10 | 7,953 | 15,010 | 1,89x |
| **avg±σ** | **8,347±0,398** | **15,464±0,509** | **1,86±0,09x** |

### tagsNotAtBranchTips_denyRules (baseline: broad allow)

- Tag cache: **cold (invalidated per call)**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 4,747 | 18,677 | 3,93x |
| 2 | 4,425 | 17,532 | 3,96x |
| 3 | 6,085 | 18,706 | 3,07x |
| 4 | 4,567 | 16,819 | 3,68x |
| 5 | 4,347 | 16,681 | 3,84x |
| 6 | 4,672 | 16,662 | 3,57x |
| 7 | 4,347 | 16,818 | 3,87x |
| 8 | 4,305 | 16,503 | 3,83x |
| 9 | 4,369 | 16,602 | 3,80x |
| 10 | 4,446 | 16,649 | 3,74x |
| **avg±σ** | **4,631±0,505** | **17,165±0,809** | **3,73±0,25x** |

### allRefsVisible_fastPathFires

- Tag cache: **warm**
- Repetitions: **10** × 50 timed iterations

| Run | Opt (ms) | Legacy (ms) | Speedup |
|---:|---:|---:|---:|
| 1 | 0,100 | 0,101 | 1,02x |
| 2 | 0,126 | 0,102 | 0,81x |
| 3 | 0,096 | 0,081 | 0,85x |
| 4 | 0,092 | 0,083 | 0,90x |
| 5 | 0,098 | 0,096 | 0,98x |
| 6 | 0,079 | 0,078 | 0,98x |
| 7 | 0,077 | 0,078 | 1,00x |
| 8 | 0,077 | 0,078 | 1,01x |
| 9 | 0,087 | 0,084 | 0,97x |
| 10 | 0,104 | 0,151 | 1,45x |
| **avg±σ** | **0,094±0,014** | **0,093±0,021** | **1,00±0,17x** |

