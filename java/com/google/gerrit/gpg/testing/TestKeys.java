// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.gpg.testing;

import com.google.common.collect.ImmutableList;

/** Common test keys used by a variety of tests. */
public class TestKeys {
  public static ImmutableList<TestKey> allValidKeys() {
    return ImmutableList.of(
        validKeyWithoutExpiration(), validKeyWithExpiration(), validKeyWithSecondUserId());
  }

  /**
   * A valid key with no expiration.
   *
   * <pre>
   * pub   2048R/46328A8C 2015-07-08
   *       Key fingerprint = 04AE A7ED 2F82 1133 E5B1  28D1 ED06 25DC 4632 8A8C
   * uid                  Testuser One &lt;test1@example.com&gt;
   * sub   2048R/F0AF69C0 2015-07-08
   * </pre>
   */
  public static TestKey validKeyWithoutExpiration() {
    return new TestKey(
        """
        -----BEGIN PGP PUBLIC KEY BLOCK-----
        Version: GnuPG v1

        mQENBFWdTIkBCADOaygDKjLuRX6LXAvBAYB91cmTf1MSlmEy+qsG3c9ijjQixPkr
        atdYkocrrT2S0R9UGjksTOI2WN5S0lQfLA1RSk63KURQE+OF+IfNqdD6nQdLBs1w
        va+GDj/uvuI05I0oXf/M7POdFphutrS4EUDBnFPj6ns/0C2sTRTxliD+Y9Y9a84V
        DfVVUbJB6wc3LP3L6ImT+cSM7dLq3hZHya+9FNeYPmPYnBrkJyqf2NDd38Sddsro
        7smw/GgCZHnnuVNS4C7NsHr6900VKC+JDtdx+fqptixcAEJWiGoQfWqU+hYmia3p
        9+Xw02+3FcjOT6ONUCmHX+xlz0pXW4iIYlPpABEBAAG0IFRlc3R1c2VyIE9uZSA8
        dGVzdDFAZXhhbXBsZS5jb20+iQE4BBMBAgAiBQJVnUyJAhsDBgsJCAcDAgYVCAIJ
        CgsEFgIDAQIeAQIXgAAKCRDtBiXcRjKKjHblB/9RaFO5+GTDIphAL/aVj2u+d8Lq
        yUpBrDp3P06QDGpKGFMAovBuh+NLH76VKNIzQLQC8rdTj651fLcLMuJ1enQ3Rblg
        RKr1oc+wqqtFHr4QyOQjE/N3C9GQjEzfqn4qnp5KtZxYFnlvU5NGehid7M1HTZMx
        jRcHbM9KQnsE5Z4fh4wmN5ynG+5nbaF4O9otPOpFzYRvIhxFmHscWyOgRaMZiYEX
        7Qkzze+scAlc9E/EWRJQIFcxnxV/SYIT4qCTT1g2aKA8OCBO/ZTOleH8SzvTODjy
        W0lGHnh/ZqH6XGVcGUaJZZ2uHTck1+czuVVShNcXPW1W20T6E9UqzHbJHN0guQEN
        BFWdTIkBCACoLVdPr3gpQwzI+2NGXjdtoyqYoPlgfeyI2M1XQD/7+rLZTbi14ZjN
        vYkS/+/oGtVEmiYOiAVTwmkjCYkKGDgNcCiJVekiPAN6JryVv488wRc999b5LpFE
        fhLGwI0YxjcS4KFFnpMC3wSb6tJUnHRLVoE5d8icdiaOpgYdp7uqWkSx2oxqHgIb
        nuyrk3ydEcS4ZeGD+w+taIxMc9F1DS9kiXALD7xWgUkmqZLEQoNgF6KlwCHXRd3m
        rBCo97sE95yKcq98ZMIWuQtTcEccZsN/6jlsei+9RI0tqs+FbZnIFm/go9zk11Vl
        IQ9QFSj6ruqoKrYvNZuDDLD1lHvZPD4/ABEBAAGJAR8EGAECAAkFAlWdTIkCGwwA
        CgkQ7QYl3EYyiox+HAf/Z/OCQO3jxALAcn3oUb1g/IlHm6qZv7RJOFUsj/16fGiF
        rRTP15zMXzyqV+L/LGV/owvOsdD/o7boZz4C/U98COx0Nl1jOrmPATOl+xqsgpEj
        Fhk+eAR7exO2XxW+u2g4cYoSMosIOX5w1GrdsxQeaZDwiSJMEOR2cVLs3YI19Ci/
        FuzActZ0wJNk0nlNF6l8CAbzwN6pM9OIc/iBIwDjz92KUco0NF8XKZnxqhH4wfHB
        PGkTx8RwOvELUTDMtvYnG5R0QtND0RbOnmp4ZVZmeOjKSLo1mZliUZB1H2PPSxrA
        0oLr8+wLntz1SU7uS4ddvhSQW+j2M/0pa352KUwmrw==
        =o/aU
        -----END PGP PUBLIC KEY BLOCK-----
        """,
        """
        -----BEGIN PGP PRIVATE KEY BLOCK-----
        Version: GnuPG v1

        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
        =MuAn
        -----END PGP PRIVATE KEY BLOCK-----
        """);
  }

  /**
   * A valid key expiring in 2065.
   *
   * <pre>
   * pub   2048R/378A0AED 2015-07-08 [expires: 2065-06-25]
   *       Key fingerprint = C378 369A CBCD 34CC 138D  90B1 4531 1A6F 378A 0AED
   * uid                  Testuser Two &lt;test2@example.com&gt;
   * sub   2048R/46D4F204 2015-07-08 [expires: 2065-06-25]
   * </pre>
   */
  public static final TestKey validKeyWithExpiration() {
    return new TestKey(
        """
        -----BEGIN PGP PUBLIC KEY BLOCK-----
        Version: GnuPG v1

        mQENBFWdTP8BCADRxNpasIv0jtNXTK6VYIS2VJ2Xk0ZD6gtxeoXCpjQ+TsB9fxh3
        vAMPt2Zu5LqoGwygKOJj1zquG8xk7GUCCHJk3+qG8xxB1xGtSz2vLyfRm7fOZmHj
        3W/C/25lynSPDrfvcwvwA4PN8iP5EWbWU10L6WOZGMwwwtVDUSEouSOw2LEepxLV
        rkKuZcyHaivheDbUlZliwe9rGXd4hh1h4qyNQWG3q+ytlL28sVkOzUh6IMBTvqhe
        IRsvxvaVSLV8jRVKfUTqw0g57ft4ZD2/L46yUTXzr9aUCBjTNxvWLlyboqql/D8P
        inp51h3cvAg7NW5RdG1GEYmylH8SygT5utPxABEBAAG0IFRlc3R1c2VyIFR3byA8
        dGVzdDJAZXhhbXBsZS5jb20+iQE+BBMBAgAoBQJVnUz/AhsDBQld/A8ABgsJCAcD
        AgYVCAIJCgsEFgIDAQIeAQIXgAAKCRBFMRpvN4oK7UZqCACWwQL/YvBK4b0m+R0d
        UdvAXeBx7DwOAnAodis9ZVqChb7RxcZQxF1Ti9mtCBPPQGuEs5wE2Ocrrq+L13r6
        bgW+1WOB1tZSDVxwL1PnZFw/SyADRIDCZrOHiAkp82UnZwWAkk39GzNJtt1wTYDZ
        FMTFUr2SPscXk1k7muS+ZfEFwNPD4tODo/poJKDYEJ80Z5UXXFQLDtsfdeIXMFIT
        449CYoq8XBMBfvyWl/LLpw0r3JI6pV/YdH3Oeuz8XkkEVzRxaxB6Zmeo5jSwjR/T
        8TKDGwwiuwiiT3SfkFSVdcjKulRuXSRNs1Ouf7/UC3cq4bG2WXWa85X1+HQRm7iu
        RHSOuQENBFWdTP8BCADhhGxAA0pX5yBHwIgM1j0gw2h5nSsopDrO6t/sbRUcNxnR
        tBScgKZnP0sjRTYEUIwmZuseHMBohtVCuMaDt06qyZDvDk/98j3AeE5t2dgFnOIe
        qCrm/6aejbFcQOpxe6U29KJRCAxuwNtB15X1VH1Kj7B0gRSTu13n/5sUsi2lunoZ
        oIvpIe9tZH4aXitCY2MCQH+hTyCyNBzlEa44kWz6LxUsPdo7I6rXkTr6Ot7wQh+9
        7HCe042GIq65h0apgujyjhJidjch5ur1mngaSNSEyvbji2MGC+cd3wAIstG5a7xP
        d9MncY5Q/eH+hn96694k5bckottSyGm/3f2Ihfj1ABEBAAGJASUEGAECAA8FAlWd
        TP8CGwwFCV38DwAACgkQRTEabzeKCu1FNwgAif4eK2v7R3QubL2S6wmb1nsgRMgV
        YoxGBeUk2EK6WZ5IPor93ySd0ixRVNMRmJ8BLH3EMjZQTzkDG+BH6zFyxo6lLHw9
        NxQjI06tqQWgyyK0mEweVwB/zqtxiB4lNUpsNbqOZWnBJ3d6o1SsnD2Q3uwvP5fb
        fSIgdmUk3c0VMdgA+KzWjPD/PJIPujE+ckHhjn5cbDNw35/FuyhkLJfqlOG7SPvM
        NmCdJ1Pcqju9t7sf6b0BGPDOCL4gpuWKK7HJz9WxngNb3FSziLbyPLk13ynADO+v
        EOR44LPyXE9kVxPusazsXlt9ayTOhELhwzw7sGFFu8E17Cpn7GnVj3tN9A==
        =1e/A
        -----END PGP PUBLIC KEY BLOCK-----
        """,
        """
        -----BEGIN PGP PRIVATE KEY BLOCK-----
        Version: GnuPG v1

        lQOYBFWdTP8BCADRxNpasIv0jtNXTK6VYIS2VJ2Xk0ZD6gtxeoXCpjQ+TsB9fxh3
        vAMPt2Zu5LqoGwygKOJj1zquG8xk7GUCCHJk3+qG8xxB1xGtSz2vLyfRm7fOZmHj
        3W/C/25lynSPDrfvcwvwA4PN8iP5EWbWU10L6WOZGMwwwtVDUSEouSOw2LEepxLV
        rkKuZcyHaivheDbUlZliwe9rGXd4hh1h4qyNQWG3q+ytlL28sVkOzUh6IMBTvqhe
        IRsvxvaVSLV8jRVKfUTqw0g57ft4ZD2/L46yUTXzr9aUCBjTNxvWLlyboqql/D8P
        inp51h3cvAg7NW5RdG1GEYmylH8SygT5utPxABEBAAEAB/0WW33OVqzEBwj9b/3X
        i+75I/Gb+yVtDZ/km2NwSJie33PirE4mTNKitTBkt1oxmphw5Yqji4gEkI/rXcqy
        OcY/fCIZ+gVT+yE2MCPF7Se4Tnl7tSvPxoUn6mOQ09AygyYVjlSCY02EAL/WxwUH
        6OCs6VYlNiBlPg7O2vHGzlzAd1aMmlG3ytlhb0SIbilaJn/wlQ2SEGySjIAP1qRH
        UXsTfW7oAjdqAY1CbCWg/0FnMBF+DnChH634dbLrS2OefcB70l61trEfRcHbMNTv
        9nVxDDCpaIdxsOfgWpe0GMG1qddRAxBIOVjNUFOL22xEFyaXnt/uagUtKQ7yejci
        bgTFBADcuhsfQaBX1G095iG2qr8Rx2T5GqNf9oZA+rbweWegqIH7MUXHI1KKwwJx
        C+rR5AgnxTSP614XI/AWB/txdelm8z0jLobpS6B1vzM2vRQ7hpwjJ3UvUkoQ5uYL
        DjaBqQi0w1cPJA79H0Yujc1zgdhATymz0uDL1BC2bHLIMuhelwQA80p07G1w8HLQ
        bTdgNwtDBMKIw39/ZyQy8ppxmpD4J6zf25r95g3er0r+njrHsa+72LnvexbedpKA
        4eiDJPN+l5jJOEWfL2WtGcqJ01bdFBPcl73tuwDJJtieUlKZH0jRjykuuUX8F+tJ
        yrmVoIGtawoeLKq3hMMOK4xi+sh3OrcD+wXIU24eO3YfUde5bhyaQplNMU5smIU0
        +looOEmFsZcTONgoN+FKrnm2TY9d4FHZ+QgtnksWHmmLxQJPtp9rHJ5BgdxMBPcK
        3w5GXRuWlOmqmnAb6vp0Q0yzVDLKCcwba0S23m3tbjZsLDcI7MG/knsp9gtL676D
        AsrpeF2+Apj0OwG0IFRlc3R1c2VyIFR3byA8dGVzdDJAZXhhbXBsZS5jb20+iQE+
        BBMBAgAoBQJVnUz/AhsDBQld/A8ABgsJCAcDAgYVCAIJCgsEFgIDAQIeAQIXgAAK
        CRBFMRpvN4oK7UZqCACWwQL/YvBK4b0m+R0dUdvAXeBx7DwOAnAodis9ZVqChb7R
        xcZQxF1Ti9mtCBPPQGuEs5wE2Ocrrq+L13r6bgW+1WOB1tZSDVxwL1PnZFw/SyAD
        RIDCZrOHiAkp82UnZwWAkk39GzNJtt1wTYDZFMTFUr2SPscXk1k7muS+ZfEFwNPD
        4tODo/poJKDYEJ80Z5UXXFQLDtsfdeIXMFIT449CYoq8XBMBfvyWl/LLpw0r3JI6
        pV/YdH3Oeuz8XkkEVzRxaxB6Zmeo5jSwjR/T8TKDGwwiuwiiT3SfkFSVdcjKulRu
        XSRNs1Ouf7/UC3cq4bG2WXWa85X1+HQRm7iuRHSOnQOYBFWdTP8BCADhhGxAA0pX
        5yBHwIgM1j0gw2h5nSsopDrO6t/sbRUcNxnRtBScgKZnP0sjRTYEUIwmZuseHMBo
        htVCuMaDt06qyZDvDk/98j3AeE5t2dgFnOIeqCrm/6aejbFcQOpxe6U29KJRCAxu
        wNtB15X1VH1Kj7B0gRSTu13n/5sUsi2lunoZoIvpIe9tZH4aXitCY2MCQH+hTyCy
        NBzlEa44kWz6LxUsPdo7I6rXkTr6Ot7wQh+97HCe042GIq65h0apgujyjhJidjch
        5ur1mngaSNSEyvbji2MGC+cd3wAIstG5a7xPd9MncY5Q/eH+hn96694k5bckottS
        yGm/3f2Ihfj1ABEBAAEAB/wP5H+mcTTrhe+57sEHuo9bQDocG+3fMtesHlRCept6
        vg1VQG4Va2GOtCCs7yMz4aNGz4jxOdB7bUkZJyFiRehG0+ahWi5b9JbSegf46Nm2
        54vt4icH2WtaEB04JaD/91k4yrunnzwVEAVDmhhIzjf4KbEjPLeBA7rF7zb0Gexq
        mdxEGO/6KdeQ6KOxkpWEqIIdl/mAGsYCprHeKL/XL+KXYr92nEbUcltmt59TTnoo
        00BQCPuHCdpcUd5nuaxpCZLM+BEpxtj0sinz0ofuWU9RI4K00R01MKXWMucdOhTZ
        kUy5dMx8wA07xbjkE/nH86N76Mty133OB7G3lBBDfO4PBADulfLzbjXUnS1kTKeP
        j/HF1E9qafzTDS/QD55OVajDq66A6zaOazKbURHNZmIqpLO4715+iNtrZQUEP3e1
        mwngeizvAv9luA9kJ1YDTCfsS5H5cYzavhfwuqBu7fQBm/PQqZplQuPCxgXEIBaY
        M0uvR0I/FSwFrepRN2IA6dAkrwQA8fpJEg8C9OLFzDf0rxV3eWwEelemN4E50Obu
        nxtg9IJWZ+QIWkRVLJ8if5+p85s2ieCw8hzEF0FyNfWUnfW5eoN4/j50loR4EbZS
        qOpUJGwr8ezyQN8PpduDOe9OQnUYAv9FY9Rk46L4937GDF2w5gdxyNdKO8yG+Z3A
        6/0DLZsEAOQsRUXIl1XLjkdugfFQ8V9Fv3AYWJt+8zknwcQ+Z3uOtyY2muCi9hX2
        BtuPojjwmN6x8wntMaUkzYHVSdz/cdx+na7VNS2kZHfnECWZGR6IHyRTJN5612yi
        e4MIdTE+BgL1HPq+VIPlMBehEksC5qM0WSq8baMsacGMYeAL8ntoRuyJASUEGAEC
        AA8FAlWdTP8CGwwFCV38DwAACgkQRTEabzeKCu1FNwgAif4eK2v7R3QubL2S6wmb
        1nsgRMgVYoxGBeUk2EK6WZ5IPor93ySd0ixRVNMRmJ8BLH3EMjZQTzkDG+BH6zFy
        xo6lLHw9NxQjI06tqQWgyyK0mEweVwB/zqtxiB4lNUpsNbqOZWnBJ3d6o1SsnD2Q
        3uwvP5fbfSIgdmUk3c0VMdgA+KzWjPD/PJIPujE+ckHhjn5cbDNw35/FuyhkLJfq
        lOG7SPvMNmCdJ1Pcqju9t7sf6b0BGPDOCL4gpuWKK7HJz9WxngNb3FSziLbyPLk1
        3ynADO+vEOR44LPyXE9kVxPusazsXlt9ayTOhELhwzw7sGFFu8E17Cpn7GnVj3tN
        9A==
        =qbV3
        -----END PGP PRIVATE KEY BLOCK-----
        """);
  }

  /**
   * A key that expired in 2006.
   *
   * <pre>
   * pub   2048R/17DE1ACD 2005-07-08 [expired: 2006-07-08]
   *       Key fingerprint = 1D9E EB79 DD38 B049 939D  9CAF 3CEC 781B 17DE 1ACD
   * uid                  Testuser Three &lt;test3@example.com&gt;
   * </pre>
   */
  public static final TestKey expiredKey() {
    return new TestKey(
        """
        -----BEGIN PGP PUBLIC KEY BLOCK-----
        Version: GnuPG v1

        mQENBELOp0UBCACxholOPWuKhK+TYb88nvLUSCMvTLIFEpb5u3Eavr0wiluEzq6H
        55nswAD3dQm8DWxA7yUlEYjPr5btpw7V9441bb1+qtgZMJ10RTdEb/WjyctdGA99
        uOKBEarWbt8W+w6lyJ9NXy5bS/x5EwHHfoTFp4ff6ffHI5hbx1a00K8oxmitgd0X
        Mx86UmauFNJYupZOZG9gEcP4RbRp7e2pm4Jy1WLEOeg9Fdgm5e5Hj2nMkCSZ9BKV
        cxuOllSVzM/Zp0/4+RS9R57jKo3/V74Whwh9yQNgL9UxdNk7L0eGqvaT3EjXxjOc
        RCeJiucGN/0W2iq+V01/QGspp4SKtAogWBozABEBAAG0IlRlc3R1c2VyIFRocmVl
        IDx0ZXN0M0BleGFtcGxlLmNvbT6JAT4EEwECACgFAkLOp0UCGwMFCQHhM4AGCwkI
        BwMCBhUIAgkKCwQWAgMBAh4BAheAAAoJEDzseBsX3hrNYg0H/2CMm5/JDQNSuRFC
        ECWLrcOeimuvwbmkonNzOkvKbGXl73GStISAksRWAHBQED1rEPC0NkFCDeVZO7df
        SYLlsqKwV6uSh05Ra0F5XeniC12YpAyzoQyCGRS2wLaS822j0zUPXA8XLaO2blCu
        R+8sNu/oecMRcFK4S9NaApi3vdqBNhLiN1/Lpqn1LfB8uIO+eaUf4PmCWbaPgzSk
        qcPfKZmocNXdgLV5Q80n3hc2y2nrl+vDW2M+eVZuDHAok2BOD9uGKFfLAbaXLbX5
        btBW2L0UHtoEyiqkRfD6lX2laSLQmA6+eup7e4GS+s0vXBuVh8XEYddV6Yjt8H7/
        2thO41K5AQ0EQs6nRQEIAM/833UHK1DuFlOm7/n18dRMvs7BkXvg+hPquKWMG3be
        eE4sh1NG5DbRCdo6iacZLarWr3FDz7J9+wswRhtHCh3pGHEuaJk52vRjQxlkNh5F
        p5u2R4WF546bWqX45xPdLfHVTPyWB9q7aVxE+6Q+MHa6lMoyTVnTVCOy3nshiihw
        dxLsxaga+QmaL0bAR+dRcO6ucj7TDQXz1AJAVp26c0LXV9iErhFuuybUZKT0a9Aj
        FoumMZ6l+k30sSdjSjpBMsNvPos0dTPPRXUMu77o5sj+pHa4o8WctgB3o7BHQELp
        KgujZ2sKC9Nm395u6Q4cqUWihzb/Y7rIRuNHJarI7vUAEQEAAYkBJQQYAQIADwUC
        Qs6nRQIbDAUJAeEzgAAKCRA87HgbF94azRiBB/4vAyOOjUjK3lDWjHGs7mvEWJI/
        1MeLlGPswCSInJBa+HMiMI4tzq+hu5ejGThojNbmnL96GdzfDkMlP4Feyxb2rjtb
        NrD/R5tlXHmjX/QLzep4LCeMziP80fu8qUeiOej/Ecdny0w365PlMdt10RaYR8VE
        ZX/DAie6JfElnfQcG5q8TIOH3i71qxV+kIoPqKWfQ0MXrNEJ3BYFfDGdUt8U1Kq9
        OuIHVRgGS7mMSyjgNqqp7MBeMY+PFFZaZel5yoYVjb9d3L8XvVv2eoa/jPj5FUEU
        kE9uxNmwaD1PiV8DvBTYI+eQL4qzfu+3NTG2SfgQYtj5oiGHw8aL3U6QHDJb
        =d/Xp
        -----END PGP PUBLIC KEY BLOCK-----
        """,
        """
        -----BEGIN PGP PRIVATE KEY BLOCK-----
        Version: GnuPG v1

        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
        HDJb
        =RrXv
        -----END PGP PRIVATE KEY BLOCK-----
        """);
  }

  /**
   * A self-revoked key with no expiration.
   *
   * <pre>
   * pub   2048R/7CA87821 2015-07-08 [revoked: 2015-07-08]
   *       Key fingerprint = E328 CAB1 1F7E B1BC 1451  ABA5 0855 2A17 7CA8 7821
   * uid                  Testuser Four &lt;test4@example.com&gt;
   * </pre>
   */
  public static final TestKey selfRevokedKey() {
    return new TestKey(
        """
        -----BEGIN PGP PUBLIC KEY BLOCK-----
        Version: GnuPG v1

        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
        =477N
        -----END PGP PUBLIC KEY BLOCK-----
        """,
        """
        -----BEGIN PGP PRIVATE KEY BLOCK-----
        Version: GnuPG v1

        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
        =5aNq
        -----END PGP PRIVATE KEY BLOCK-----
        """);
  }

  /**
   * A key with an additional user ID.
   *
   * <pre>{@code
   * pub   2048R/98C51DBF 2015-07-30
   *       Key fingerprint = 42B3 294D 1924 D7EB AF4A  A99F 5024 BB44 98C5 1DBF
   * uid                  foo:myId
   * uid                  Testuser Five <test5@example.com>
   * sub   2048R/C781A9E3 2015-07-30
   * }</pre>
   */
  public static TestKey validKeyWithSecondUserId() {
    return new TestKey(
        """
        -----BEGIN PGP PUBLIC KEY BLOCK-----
        Version: GnuPG v1

        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
        54w16Vp/SVViJs7nTMSug0WREyd2
        =ldwB
        -----END PGP PUBLIC KEY BLOCK-----
        """,
        """
        -----BEGIN PGP PRIVATE KEY BLOCK-----
        Version: GnuPG v1

        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
        =uND5
        -----END PGP PRIVATE KEY BLOCK-----
        """);
  }

  /**
   * A key revoked by a valid key, due to key compromise.
   *
   * <p>Revoked by {@link #validKeyWithoutExpiration()}.
   *
   * <pre>
   * pub   2048R/3434B39F 2015-10-20 [revoked: 2015-10-20]
   *       Key fingerprint = 931F 047D 7D01 DDEF 367A  8D90 8C4F D28E 3434 B39F
   * uid                  Testuser Six &lt;test6@example.com&gt;
   * </pre>
   */
  public static TestKey revokedCompromisedKey() throws Exception {
    return new TestKey(
        """
        -----BEGIN PGP PUBLIC KEY BLOCK-----
        Version: GnuPG v1

        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
        tULvIEIjNNP8Qizfc4u2O8h7HP2b3yYSrp9MMQ==
        =Dxr7
        -----END PGP PUBLIC KEY BLOCK-----
        """,
        """
        -----BEGIN PGP PRIVATE KEY BLOCK-----
        Version: GnuPG v1

        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
        i7Y7yHsc/ZvfJhKun0wx
        =M/kw
        -----END PGP PRIVATE KEY BLOCK-----
        """);
  }

  /**
   * A key revoked by a valid key, due to no longer being used.
   *
   * <p>Revoked by {@link #validKeyWithoutExpiration()}.
   *
   * <pre>
   * pub   2048R/3D6C52D0 2015-10-20 [revoked: 2015-10-20]
   *       Key fingerprint = 32DB 6C31 2ED7 A98D 11B2  43EA FAD2 ABE2 3D6C 52D0
   * uid                  Testuser Seven &lt;test7@example.com&gt;
   * </pre>
   */
  public static TestKey revokedNoLongerUsedKey() throws Exception {
    return new TestKey(
        """
        -----BEGIN PGP PUBLIC KEY BLOCK-----
        Version: GnuPG v1

        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
        +B6paw/s8P7XCWznTBRdlFWWgrhcpzQ8fefC
        =CHer
        -----END PGP PUBLIC KEY BLOCK-----
        """,
        """
        -----BEGIN PGP PRIVATE KEY BLOCK-----
        Version: GnuPG v1

        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
        bOdMFF2UVZaCuFynNDx958I=
        =aoJv
        -----END PGP PRIVATE KEY BLOCK-----
        """);
  }

  /**
   * Key revoked by an expired key, after that key's expiration.
   *
   * <p>Revoked by {@link #expiredKey()}.
   *
   * <pre>
   * pub   2048R/78BF7D7E 2005-08-01 [revoked: 2015-10-20]
   *       Key fingerprint = 916F AB22 5BE7 7585 F59A  994C 001A DF8B 78BF 7D7E
   * uid                  Testuser Eight &lt;test8@example.com&gt;
   * </pre>
   */
  public static TestKey keyRevokedByExpiredKeyAfterExpiration() throws Exception {
    return new TestKey(
        """
        -----BEGIN PGP PUBLIC KEY BLOCK-----
        Version: GnuPG v1

        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
        0d0PyzZT3676mQwmFoebMR9vACAeHjvDxD4F
        =ihWb
        -----END PGP PUBLIC KEY BLOCK-----
        """,
        """
        -----BEGIN PGP PRIVATE KEY BLOCK-----
        Version: GnuPG v1

        lQOYBELuRwABCAC56yhFKybBtuKT4nyb7RdLE98pZR54aGjcDcKH3VKVyBF8Z4Kx
        ptd7Sre0mLPCQiNWVOmCT+JG7GKVE6YeFmyXDUnhX9w4+HAeDEh23S4u9JvwWaF+
        wlJ6jLq/oe5gdT1F6Y2yqNpQ6CztOw52Ko9KSYz7/1zBMPcCkl/4k15ee4iebVdq
        c7qT5Qt49Poiozh0DI5prPQ624uckHkz2mXshjWQVuHWwrkIkCJZ2I/KQN2kBjKw
        /ALxumaWmiB9lQ0nIwLuGzHCh0Xg5RxuCrK8fJp47Aza3ikVuYlNzSxhJVav3OtK
        gftBihQXUlY3Uy/4QTCeH/BdVs5OALtXL3VhABEBAAEAB/wLr88oGuxsoqIHRQZL
        eGm9jc4aQGmcDMcjpwdGilhrwyfrO6f84hWbQdD+rJcnI8hsH7oOd5ZMGkWfpJyt
        eUAh9iNB5ChYGfDVSLUg6KojqDtprj6vNMihvLkr/OI6xL/hZksikwfnLFMPpgXU
        knwPocQ3nn+egsUSL7CR8/SLiIm4MC0brer6jhDxB5LKweExNlfTe4c0MDeYTsWt
        0WGzNPlvRZQXRotJzqemt3wdNZXUnCKR0n7pSQ8EhZr2O6NXr+mUgp6PIOE/3un2
        YGiBEf5uy3qEFe7FjEGIHz+Z3ySRdUDfHOk82TKAzynoJIxRUvLIYVNw4eFB3l5U
        s1w5BADUzfciG7RVLa8UFKJfqQ/5M06QmdS1v1/hMQXg38+3vKe8RgfSSnMJ08Sc
        eAEsmugwpNXAxgRKHcmWzN3NMBHhE3KiyiogWaMGqmSo6swFpu0+dwMvZSxMlfD+
        ka/BWt8YsUdrqW06ow39aTgCV+icbNRV81C7NKe7u0X1JDx2CQQA36gbdo62h/Wd
        gJI8kdz/se3xrt8x6RoWvOnWPNmsZR5XkDqAMTL1dWiEEA/dQTphMcgAe9z3WaP+
        F1TPAfounbiurGCcS3kxJ5tY7ojyU7nYz4DA/V2OU0C/LUoLXhttG5HM+m/i3qn4
        K9bBoWIQY1ijliS7cTSwNqd6IHaQGpkEAMnp5GwSGhY+kUuLw06hmH4xnsuf6agz
        AfhbPylB2nf/ZaX6dt6/mFEAkvQNahcoWEskfS3LGCD8jHm8PvF8K0mciXPDweq2
        gW3/irE0RXNwn3Oa222VSvcgUlocBm9InkfvpFXh20OYFe3dFH7uYkwUqIHJeXjw
        TjpXUX/vC5QJQOyJATcEHwECACEFAkLuVRAXDIABHZ7red04sEmTnZyvPOx4Gxfe
        Gs0CBwAACgkQABrfi3i/fX4BLgf/THdj0Ter0NHkUK5hNcbgEfKsAujZYqg/QJ9R
        Yv3wXD1PGXSEX73nCuI2WeZ3bO8zSq4ezvzHj/F6FA7zxzDSWo/HdEBGr+O7n9Vs
        6Og2Vwe98ZhmIR3JY7Ae7s7yxmt6cUON4OCUPo0CuuOdw5wLnVcg9JJMu9VClJq5
        DUVLi1qflDhpYxuzgPMT5Wq7bEyQ79f22hgBkrPJY/b05ySmi4xu3cZSdLr+jQDx
        nuNVf5c4iTdeiU8uL23NNAHXZeXbeaAW95qsOiC5HYG7ZJuZMm12JfPwvdLniQa/
        FWpqTm44/Z1qDNS8GKpHhJWgB0ahe3pMjqakD6p1iY8wby/bVLQiVGVzdHVzZXIg
        RWlnaHQgPHRlc3Q4QGV4YW1wbGUuY29tPokBOAQTAQIAIgUCQu5HAAIbAwYLCQgH
        AwIGFQgCCQoLBBYCAwECHgECF4AACgkQABrfi3i/fX7z+wgAi6p351TyT1zlVDXa
        Tsh9kYZhAcXYyT/wU24/uEU/9U5cErdAjEh8YQ4dsNfq4jG51gxoOafZQlt0iol3
        w25AHKHWCpd1f/Ge15Rr7h7tTq5OOSn8iPlTNF53yy4g0Dc3NU7LqrAK7cNM5chl
        yOI5m04DkZOobpmdoM/SHAOywmn4tchhMmCy+d4lAUZJ1CGElK/X2hXQk9Jtvplz
        uAQge09xokeO5lAS/5NFxGsj0c9MXQy+A5iAgOjaxtxIVX7yagqwcwL6PUea66qC
        b/XIoYhmu3RXvmwP0di7S2JLf5aWyvLfLWGD/ZcFBvCCHqxP+NaSlZqvv67M3E7k
        Z0VisJ0DmARC7kcAAQgAs8VN51t/Ir70HflSj1w+yYVreZPeqn9sBC2c6OJNNOIg
        7e27QAudoGN/o6d8BXUeYbPqFExErgsbIeQ1NoYM00+hc7v630NbJxIeawIvl3nO
        ntV69ATww9HzMcoiXZhCULN3LlNZJSG6cp3iC4M1n0aI5Lo1D195hn0NDWTMLyee
        7uMuRHbM1PTaxhSS44JlSS1CFU2zNSVq90gWO9OhmjdE9x1v8TeRNPLZH3GLz0Be
        NZl/eVIBl7eKCQuOHCx367dFHHSm04nKbvNiXll1RmThCtLsU8jrMQGmNrBtLj+v
        q4Rbz0/dACRqcMiuJ0gBk/xrAtlTe5I+0zA8gmlqjQARAQABAAf+JNVkZOcGYaQm
        eI3BMMaBxuCjaMG3ec+p3iFKaR0VHKTIgneXSkQXA+nfGTUT4DpjAznN2GLYH6D+
        6i7MCGPm9NT4C7KUcHJoltTLjrlf7vVyNHEhRCZO/pBh9+2mpO6xh799x+wj88u5
        XAqlah50OjJFkjfk70VsrPWqWvgwLejkaQpGbE+pdL+vjy+ol5FHzidzmJvsXDR1
        I1as0vBu5g2XPpexyVanmHJglZdZX07OPYQBhxQKuPXT/2/IRnXsXEpitk4IyJT0
        U5D/iedEUldhBByep1lBcJnAap0CP7iuu2CYhRp6V2wVvdweNPng5Eo7f7LNyjnX
        UMAeaeCjAQQA1A0iKtg3Grxc9+lpFl1znc2/kO3p6ixM13uUvci+yGFNJJninnxo
        99KXEzqqVD0zerjiyyegQmzpITE/+hFIOJZInxEH08WQwZstV/KYeRSJkXf0Um48
        E+Zrh8fpJVW1w3ZCw9Ee2yE6fEhAA4w66+50pM+vBXanWOrG1HDrkxEEANkHc2Rz
        YJsO4v63xo/7/njLSQ31miOglb99ACKBA0Yl/jvj2KqLcomKILqvK3DKP+BHNq86
        LUBUglyKjKuj0wkSWT0tCnfgLzysUpowcoyFhJ36KzAz8hjqIn3TQpMF21HvkZdG
        Mtkcyhu5UDvbfOuWOBaKIeNQWCWv1rNzMme9A/9zU1+esEhKwGWEqa3/B/Te/xQh
        alk180n74sTZid6lXD8o8cEei0CUq7zBSV0P8v6kk8PP9/XyLRl3Rqa95fESUWrL
        xD6TBY1JlHBZS+N6rN/7Ilf5EXSELmnbDFsVxkNGp4elKxajvZxC6uEWYBu62AYy
        wS0dj8mZR3faCEps90YXiQEfBBgBAgAJBQJC7kcAAhsMAAoJEAAa34t4v31+qkcH
        /A5eF0w22B5VbfxsuTxiFwQHbcVbp8OMWMElv2b/KpFOHkIh4bZubb2h9Pg9NqY9
        vwa35i4MudBDcraNRFqTE0NxjCK6u9H+gdV3GwFFTs/HtQ95mTcEblWcglFPX3x0
        XsxYUn7HnAds1fAzwMO9TBONcsf9OUaXVciF5JMcJh30+trh6MShySSsNpmU8JB1
        PBj4/sraJ5vm2yo2kmRB4GuCUXZt6Zj8K1Aecz2rBrpkmqTJg1EXWeRJiffoE+42
        nK1jDUJpBX0Zt4+6A8e2yEQIvADXrRDOohJBRwmUpe21SpQaeg7R3Q/LNlPfrvqZ
        DCYWh5sxH28AIB4eO8PEPgU=
        =cSfw
        -----END PGP PRIVATE KEY BLOCK-----
        """);
  }

  /**
   * Key revoked by an expired key, before that key's expiration.
   *
   * <p>Revoked by {@link #expiredKey()}.
   *
   * <pre>
   * pub   2048R/C43BF2E1 2005-08-01 [revoked: 2005-08-01]
   *       Key fingerprint = 916D 6AD6 36A5 CBA6 B5A6  7274 6040 8661 C43B F2E1
   * uid                  Testuser Nine &lt;test9@example.com&gt;
   * </pre>
   */
  public static TestKey keyRevokedByExpiredKeyBeforeExpiration() throws Exception {
    return new TestKey(
        """
        -----BEGIN PGP PUBLIC KEY BLOCK-----
        Version: GnuPG v1

        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
        REW68wsnWjwTtrPG8bqo6cCsOzqGYVt81eU=
        =FnZg
        -----END PGP PUBLIC KEY BLOCK-----
        """,
        """
        -----BEGIN PGP PRIVATE KEY BLOCK-----
        Version: GnuPG v1

        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
        s8bxuqjpwKw7OoZhW3zV5Q==
        =JxsF
        -----END PGP PRIVATE KEY BLOCK-----
        """);
  }

  /**
   * Master Key without expiration with subkey with expiration.
   *
   * <pre>{@code
   * pub   rsa1024 2018-11-17 [C]
   *       5734 2C37 982A 843B 19C0  622B 6AAF 2D26 B481 02DB
   * uid            [ultimate] Testuser 10 <testuser10@example.com>
   * sub   rsa1024 2018-11-17 [S] [expires: 2065-11-05]
   *       0A4A 9660 1B96 2DFC E898  E686 4305 C92E 626E B485
   * }</pre>
   */
  public static TestKey validKeyWithoutExpirationWithSubkeyWithExpiration() throws Exception {
    return new TestKey(
        """
        -----BEGIN PGP PUBLIC KEY BLOCK-----

        mI0EW+98GgEEALLn87xX++daic3AzKwM7nY50Mx2eTaEZPlnaDAVvFlhbZuPG+n5
        g93vYX3wEfnFxI7IBEe7VMT1AyszLZgpFmbzW8eGQxCGpRd1hYrUUlC0IkGAwG9v
        LQB85GDDZUUH4p+A4oHX0yUm8iCpbO9+D82xzNDe/D8Xbw1foWMWGonLABEBAAG0
        JFRlc3R1c2VyIDEwIDx0ZXN0dXNlcjEwQGV4YW1wbGUuY29tPojOBBMBCAA4FiEE
        VzQsN5gqhDsZwGIraq8tJrSBAtsFAlvvfBoCGwEFCwkIBwIGFQoJCAsCBBYCAwEC
        HgECF4AACgkQaq8tJrSBAtvfFAP/VqV77KQZp9rjSGStDpxxlatr4Y5nrRBZfV5v
        jpAjwusIHRjr0OWXLxX7NDLYd+oIjhLFn26Lux1UXOQT+rGRPwnxoJZWrpDQidP7
        fDgfqnNa5UQGvoBPSIVEK1l0DlYAOUuciwz3HdMkeMuvEVEdyg7nOiVd1bF9V9i/
        8v7ABV24jQRb73xXAQQAssv5gwxWx5J0q4gGcqMIaJKzBaHAjiK3ryH6qnFQpsf1
        ODtU+a4NxFJsXGOd6jHEhBEHPgWAaaKZ7PEJVnwA/XOhPG+q9YimAbbZS0qmC/LH
        DpFtFbsJsMKZbIC69j9OcbmalIowspFQBVeAankGFReZVhh99Z/o81Y+Twm9eisA
        EQEAAYkBcQQYAQgAJhYhBFc0LDeYKoQ7GcBiK2qvLSa0gQLbBQJb73xXAhsCBQlY
        WHSAAL8JEGqvLSa0gQLbtCAEGQEIAB0WIQQKSpZgG5Yt/OiY5oZDBckuYm60hQUC
        W+98VwAKCRBDBckuYm60hafuBACSkvwXAYfxvAf7IOK6+Sp3oWkrq6vsjH5K7oup
        TimR1cVgN1CEAWh82UBCg3zR5Q5BAnvnjeugdQVrAY+sftkaoy8qO5YYHCPtHtQK
        mXGWM7Q33hU1E7IfgU06qkFLhIOL3Vwr8jOuOzHv0M3PbLNr7lOCaIJ7uCjPBZuo
        qRwqjHt2BACuXwA8RHbRGAxC65YgoSjGNu/da3q2J26E57KfSFprQ4TzAg33U4Ws
        qdx2vbbJxy1bfTYxb0AYXe/+k23W7EIdBtMGOYwrX01oTfSIKbM+gDrHswSYXdOy
        ziatLaUBSQfyG656lGGZO/aArLZb4dgGeBHBwhXTufFDIYl3X94zfQ==
        =BG9Z
        -----END PGP PUBLIC KEY BLOCK-----\
        """,
        """
        -----BEGIN PGP PRIVATE KEY BLOCK-----

        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
        lGGZO/aArLZb4dgGeBHBwhXTufFDIYl3X94zfQ==
        =RbPm
        -----END PGP PRIVATE KEY BLOCK-----\
        """);
  }
}
