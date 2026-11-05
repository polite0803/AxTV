import re
s = open('D:/OneManager/AxTV/src/app/p000/n11.java', encoding='utf-8').read()
i = s.index('https://mssdk.bytedance.com/web/r/token')
seg = s[i:i+14000]
marker = 'strData\\":\\"'
j = seg.index(marker) + len(marker)
e = seg.index('\\"', j)
data = seg[k] if False else seg[j:e]
print('len', len(data))
open('D:/OneManager/AxTV/refactor/.strdata.txt', 'w', encoding='utf-8').write(data)
m = re.search(r'magic\\":(\d+)', seg)
print('magic', m.group(1) if m else None)
print('head', data[:60])
print('tail', data[-40:])
