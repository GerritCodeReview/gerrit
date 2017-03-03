# -*- coding: utf-8
from __future__ import print_function

ori_lines_list = []

class ship():
  def __init__(self):
    self.shipclass = None
    self.name = None
    self.sname = None
    self.id = None
    self.sid = None
    self.color = None
    self.scolor = None
    self.cv = None
    self.person = None
    self.sperson = None
    self.country = None
    self.type = None
    self.slevel = None
    self.hp = None
    self.shp = None
    self.at = None
    self.at_u = None
    self.sat = None
    self.sat_u = None
    self.am = None
    self.am_u = None
    self.sam = None
    self.sam_u = None
    self.t = None
    self.t_u = None
    self.st = None
    self.st_u = None
    self.dodge = None
    self.dodge_u = None
    self.sdodge = None
    self.sdodge_u = None
    self.ad = None
    self.ad_u = None
    self.carry = None
    self.scarry = None
    self.ob = None
    self.ob_u = None
    self.sob = None
    self.sob_u = None
    self.speed = None
    self.sspeed = None
    self.ob = None
    self.ob_u = None
    self.sob = None
    self.sob_u = None
    self.range = None
    self.srange = None
    self.lucky = None
    self.slucky = None
    self.fuel = None
    self.sfuel = None
    self.amm = None
    self.samm = None
    self.s = None
    self.core_type = None
    self.core_sum = None
    self.arm_sum = None
    self.sarm_sum = None
    self.arm_type = None
    self.sarm_type = None
    self.skill_name = []
    self.skill_des = []
    self.build = None
    self.drop = None

def main():
  print_line = ''
  print_line += 'shipclass@'
  print_line += 'name@'
  print_line += 'id@'
  print_line += 'sid@'
  print_line += 'color@'
  print_line += 'scolor@'
  print_line += 'cv@'
  print_line += 'person@'
  print_line += 'sperson@'
  print_line += 'country@'
  print_line += 'type@'
  print_line += 'sname@'
  print_line += 'slevel@'
  print_line += 'hp@'
  print_line += 'shp@'
  print_line += 'at@'
  print_line += 'at_u@'
  print_line += 'sat@'
  print_line += 'sat_u@'
  print_line += 'am@'
  print_line += 'am_u@'
  print_line += 'sam@'
  print_line += 'sam_u@'
  print_line += 't@'
  print_line += 't_u@'
  print_line += 'st@'
  print_line += 'st_u@'
  print_line += 'dodge@'
  print_line += 'dodge_u@'
  print_line += 'sdodge@'
  print_line += 'sdodge_u@'
  print_line += 'ad@'
  print_line += 'ad_u@'
  print_line += 'sad@'
  print_line += 'sad_u@'
  print_line += 'carry@'
  print_line += 'scarry@'
  print_line += 'ob@'
  print_line += 'ob_u@'
  print_line += 'sob@'
  print_line += 'sob_u@'
  print_line += 'speed@'
  print_line += 'sspeed@'
  print_line += 'ob@'
  print_line += 'ob_u@'
  print_line += 'sob@'
  print_line += 'sob_u@'
  print_line += 'range@'
  print_line += 'srange@'
  print_line += 'lucky@'
  print_line += 'slucky@'
  print_line += 'fuel@'
  print_line += 'sfuel@'
  print_line += 'amm@'
  print_line += 'samm@'
  print_line += 's@'
  print_line += 'core_sum@'
  print_line += 'core_type@'
  print_line += 'arm_sum@'
  print_line += 'sarm_sum@'
  print_line += 'arm_type@'
  print_line += 'sarm_type@'
  print_line += 'skill_name 0@'
  print_line += 'skill_des 0@'
  print_line += 'skill_name 1@'
  print_line += 'skill_des 1\n'

  for ori_lines in ori_lines_list:
    if not ori_lines:
      continue
    lines = ori_lines.split('\n')
    s = ship()
    s.shipclass = lines[0].split('——')[0]
    s.name = lines[0].split('——')[1]
    s.id = lines[1].split('图鉴编号：')[-1].split(' ')[0].split('→')[0]
    s.color = lines[1].split('稀有度：')[-1].split('→')[0]
    if '→' in lines[1]:
      s.sid = lines[1].split('图鉴编号：')[-1].split(' ')[0].split('→')[1]
    else:
      s.sid = 'N/A'
    s.cv = lines[2].split('CV：')[-1].split(' ')[0]
    s.person = lines[2].split('人设：')[-1].split('→')[0]
    s.sperson = lines[2].split('人设：')[-1].split('→')[-1]
    s.country = lines[3].split('国籍：')[-1].split(' ')[0]
    s.type = lines[3].split('类型：')[-1]
    s.hp = lines[5].split('耐久  ')[-1].split(' ')[0].split('→')[0]
    s.at = lines[5].split('火力  ')[-1].split('→')[0].split('(')[0]
    s.at_u = lines[5].split('火力  ')[-1].split('→')[0].split('(')[-1].split(')')[0]
    s.sat = lines[5].split('火力  ')[-1].split('→')[-1].split('(')[0]
    s.sat_u = lines[5].split('火力  ')[-1].split('→')[-1].split('(')[-1].split(')')[0]
    s.am = lines[6].split('装甲  ')[-1].split(' ')[0].split('→')[0].split('(')[0]
    s.am_u = lines[6].split('装甲  ')[-1].split(' ')[0].split('→')[0].split('(')[-1].split(')')[0]
    s.sam = lines[6].split('装甲  ')[-1].split(' ')[0].split('→')[-1].split('(')[0]
    s.sam_u = lines[6].split('装甲  ')[-1].split(' ')[0].split('→')[-1].split('(')[-1].split(')')[0]
    s.t = lines[6].split('鱼雷  ')[-1].split('→')[0].split('(')[0]
    s.t_u = lines[6].split('鱼雷  ')[-1].split('→')[0].split('(')[-1].split(')')[0]
    s.st = lines[6].split('鱼雷  ')[-1].split('→')[-1].split('(')[0]
    s.st_u = lines[6].split('鱼雷  ')[-1].split('→')[-1].split('(')[-1].split(')')[0]
    s.dodge = lines[7].split('回避  ')[-1].split(' ')[0].split('→')[0].split('(')[0]
    s.dodge_u = lines[7].split('回避  ')[-1].split(' ')[0].split('→')[0].split('(')[-1].split(')')[0]
    s.sdodge = lines[7].split('回避  ')[-1].split(' ')[0].split('→')[-1].split('(')[0]
    s.sdodge_u = lines[7].split('回避  ')[-1].split(' ')[0].split('→')[-1].split('(')[-1].split(')')[0]
    s.ad = lines[7].split('对空  ')[-1].split('→')[0].split('(')[0]
    s.ad_u = lines[7].split('对空  ')[-1].split('→')[0].split('(')[-1].split(')')[0]
    s.sad = lines[7].split('对空  ')[-1].split('→')[-1].split('(')[0]
    s.sad_u = lines[7].split('对空  ')[-1].split('→')[-1].split('(')[-1].split(')')[0]
    s.carry = lines[8].split('搭载  ')[-1].split(' ')[0].split('→')[0]
    s.ob = lines[8].split('对潜  ')[-1].split('→')[0].split('(')[0]
    s.ob_u = lines[8].split('对潜  ')[-1].split('→')[0].split('(')[-1].split(')')[0]
    s.sob = lines[8].split('对潜  ')[-1].split('→')[-1].split('(')[0]
    s.sob_u = lines[8].split('对潜  ')[-1].split('→')[-1].split('(')[-1].split(')')[0]
    s.speed = lines[9].split('航速  ')[-1].split(' ')[0].split('→')[0]

    s.ob = lines[9].split('索敌  ')[-1].split('→')[0].split('(')[0]
    s.ob_u = lines[9].split('索敌  ')[-1].split('→')[0].split('(')[-1].split(')')[0]
    s.sob = lines[9].split('索敌  ')[-1].split('→')[-1].split('(')[0]
    s.sob_u = lines[9].split('索敌  ')[-1].split('→')[-1].split('(')[-1].split(')')[0]
    s.range = lines[10].split('射程  ')[-1].split(' ')[0].split('→')[0]
    s.lucky = lines[10].split('幸运  ')[-1].split('→')[0]
    s.slucky = lines[10].split('幸运  ')[-1].split('→')[-1]
    s.fuel = lines[12].split('燃料  ')[-1].split(' ')[0].split('→')[0]
    s.sfuel = lines[12].split('燃料  ')[-1].split(' ')[0].split('→')[-1]
    s.amm = lines[12].split('弹药  ')[-1].split('→')[0]
    s.samm = lines[12].split('弹药  ')[-1].split('→')[-1]
    s.s = lines[17].split(' ')[0]
    s.core_sum = lines[17].split('*')[-1]
    s.core_type = lines[17].split('*')[0].split(' ')[-1]
    s.arm_sum = [lines[19].split('→')[0],
                 lines[21].split('→')[0],
                 lines[23].split('→')[0],
                 lines[25].split('→')[0]]
    s.sarm_sum = [lines[19].split('→')[-1],
                  lines[21].split('→')[-1],
                  lines[23].split('→')[-1],
                  lines[25].split('→')[-1]]
    s.arm_type = [lines[20].split('→')[0].strip(' '),
                  lines[22].split('→')[0].strip(' '),
                  lines[24].split('→')[0].strip(' '),
                  lines[26].split('→')[0].strip(' ')]
    s.sarm_type = [lines[20].split('→')[-1].strip(' '),
                   lines[22].split('→')[-1].strip(' '),
                   lines[24].split('→')[-1].strip(' '),
                   lines[26].split('→')[-1].strip(' ')]
    for line in lines:
      if '技能名称  ' in line:
        s.skill_name.append(line.split('技能名称  ')[-1])
      elif '技能描述  ' in line:
        s.skill_des.append(line.split('技能描述  ')[-1])
    while len(s.skill_name) < 2:
      s.skill_name.append('N/A')
      s.skill_des.append('N/A')
    if not 'N/A' == s.sid:
      s.scolor = lines[1].split('稀有度：')[-1].split('→')[-1]
      s.shp = lines[5].split('耐久  ')[-1].split(' ')[0].split('→')[1]
      s.scarry = lines[8].split('搭载  ')[-1].split(' ')[0].split('→')[1]
      s.sspeed = lines[9].split('航速  ')[-1].split(' ')[0].split('→')[1]
      s.srange = lines[10].split('射程  ')[-1].split(' ')[0].split('→')[1]
      s.sname = lines[4].split('→')[-1].split('(')[0]
      s.slevel = lines[4].split('(Lv ')[-1].split(')')[0]
    else:
      s.scolor = s.color
      s.shp = s.hp
      s.scarry = s.carry
      s.sspeed = s.speed
      s.srange = s.range
      s.sname = s.name
      s.slevel = 'N/A'

    print_line += s.shipclass + '@'
    print_line += s.name + '@'
    print_line += s.id + '@'
    print_line += s.sid + '@'
    print_line += s.color + '@'
    print_line += s.scolor + '@'
    print_line += s.cv + '@'
    print_line += s.person + '@'
    print_line += s.sperson + '@'
    print_line += s.country + '@'
    print_line += s.type + '@'
    print_line += s.sname + '@'
    print_line += s.slevel + '@'
    print_line += s.hp + '@'
    print_line += s.shp + '@'
    print_line += s.at + '@'
    print_line += s.at_u + '@'
    print_line += s.sat + '@'
    print_line += s.sat_u + '@'
    print_line += s.am + '@'
    print_line += s.am_u + '@'
    print_line += s.sam + '@'
    print_line += s.sam_u + '@'
    print_line += s.t + '@'
    print_line += s.t_u + '@'
    print_line += s.st + '@'
    print_line += s.st_u + '@'
    print_line += s.dodge + '@'
    print_line += s.dodge_u + '@'
    print_line += s.sdodge + '@'
    print_line += s.sdodge_u + '@'
    print_line += s.ad + '@'
    print_line += s.ad_u + '@'
    print_line += s.sad + '@'
    print_line += s.sad_u + '@'
    print_line += s.carry + '@'
    print_line += s.scarry + '@'
    print_line += s.ob + '@'
    print_line += s.ob_u + '@'
    print_line += s.sob + '@'
    print_line += s.sob_u + '@'
    print_line += s.speed + '@'
    print_line += s.sspeed + '@'
    print_line += s.ob + '@'
    print_line += s.ob_u + '@'
    print_line += s.sob + '@'
    print_line += s.sob_u + '@'
    print_line += s.range + '@'
    print_line += s.srange + '@'
    print_line += s.lucky + '@'
    print_line += s.slucky + '@'
    print_line += s.fuel + '@'
    print_line += s.sfuel + '@'
    print_line += s.amm + '@'
    print_line += s.samm + '@'
    print_line += s.s + '@'
    print_line += s.core_sum + '@'
    print_line += s.core_type + '@'
    print_line += str(s.arm_sum) + '@'
    print_line += str(s.sarm_sum) + '@'
    print_line += s.arm_type[0] + ' ' + s.arm_type[1] + ' ' + s.arm_type[2] + ' ' + s.arm_type[3] + '@'
    print_line += s.sarm_type[0] + ' ' + s.sarm_type[1] + ' ' + s.sarm_type[2] + ' ' + s.sarm_type[3] + '@'
    print_line += s.skill_name[0] + '@'
    print_line += s.skill_des[0] + '@'
    print_line += s.skill_name[1] + '@'
    print_line += s.skill_des[1] + '\n'
  print(print_line)

if __name__ == '__main__':
  ori_lines_list.append('''海军上将级1号舰——胡德
图鉴编号：1→1001 稀有度：5→6
CV：N/A  人设：JS桑→15k
国籍：E国 类型：战列巡洋舰
改造等级：胡德→胡德·改(Lv 75)
耐久  75→80 火力  68(93)→78(103)
装甲  65(80)→72(87) 鱼雷  0(0)→0(0)
回避  27(57)→33(65) 对空  40(70)→55(85)
搭载  12→12 对潜  0(0)→0(0)
航速  31→32 索敌  13(38)→18(43)
射程  长→长 幸运  5→8
最大消费量
燃料  70→70 弹药  120→120
强化加成
火力  73→83 鱼雷  0→0
装甲  65→72 对空  24→47
改造
0/0/200/0 战列改造核心*20
搭载  装备
3→3
 英国双联15英寸炮 → 英国双联15英寸炮(改)
3→3
 英国双联4英寸炮 → 附加装甲(大型)
3→3
 海象式 → 英国三联15英寸炮(试作型)
3→3
未装备→  "生姜&鱼饼"
技能（改造后习得）
技能名称  皇家海军的荣耀农药
技能描述  当胡德作为旗舰时 为队伍中所有舰船附加5/7.5/10%被暴击率 为队伍中英国舰船附加10/15/20%暴击率 其他国家的舰船增加5%/7.5%/10%的暴击率
技能名称  皇家巡游飚车
技能描述  当胡德作为旗舰时 为队伍中所有舰船增加2/3/4航速
获得方式  建造(4小时30分，极低概率) 掉落(1-4、2-3、2-4、3-2之后全图 2015夏季活动E3)
友情链接：舰少资料库

阅读更多：战舰少女:胡德（https://zh.moegirl.org/%E6%88%98%E8%88%B0%E5%B0%91%E5%A5%B3%3A%E8%83%A1%E5%BE%B7）
本文引自萌娘百科（https://zh.moegirl.org/），文字内容遵守【知识共享 署名-非商业性使用-相同方式共享 3.0】协议。''')


  ori_lines_list.append('''扶桑型1番舰——扶桑
图鉴编号：2  稀有度：3
CV：N/A  人设：STMaster
国籍：J国 类型：战列舰
改造等级：不可
耐久  67  火力  74(94)
装甲  59(79)  鱼雷  0(0)
回避  19(39)  对空  23(53)
搭载  12  对潜  0(0)
航速  25.0  索敌  9(34)
射程  长 幸运  5
最大消费量
燃料  85  弹药  120
强化加成
火力  74  鱼雷  0
装甲  59  对空  12
改造
N/A
搭载  装备
3
 日本35.6厘米连装炮
3
 日本15.2厘米单装炮
3
未装备
3
未装备
获得方式  建造（4小时20分）掉落（2-2、2-3、2-4、2-5、3-2后全图 2015春季活动E1～E4)
友情链接：舰少资料库

阅读更多：战舰少女:扶桑（https://zh.moegirl.org/%E6%88%98%E8%88%B0%E5%B0%91%E5%A5%B3%3A%E6%89%B6%E6%A1%91）
本文引自萌娘百科（https://zh.moegirl.org/），文字内容遵守【知识共享 署名-非商业性使用-相同方式共享 3.0】协议。''')

  ori_lines_list.append('''扶桑型2番舰——山城
图鉴编号：3  稀有度：3
CV：N/A  人设：STmaster
国籍：J国 类型：战列舰
改造等级：不可
耐久  67  火力  74(94)
装甲  59(79)  鱼雷  0(0)
回避  19(39)  对空  23(53)
搭载  12  对潜  0(0)
航速  25  索敌  9(34)
射程  长 幸运  5
最大消费量
燃料  85  弹药  120
强化加成
火力  74  鱼雷  0
装甲  59  对空  12
改造
N/A
搭载  装备
3
 日本35.6厘米连装炮
3
 日本15.2厘米单装炮
3
未装备
3
未装备
获得方式  建造（4小时20分）掉落（2-2、2-3、3-2后全图 2015春季活动E1～E4)
友情链接：舰少资料库

阅读更多：战舰少女:山城（https://zh.moegirl.org/%E6%88%98%E8%88%B0%E5%B0%91%E5%A5%B3%3A%E5%B1%B1%E5%9F%8E）
本文引自萌娘百科（https://zh.moegirl.org/），文字内容遵守【知识共享 署名-非商业性使用-相同方式共享 3.0】协议。''')

  ori_lines_list.append('''伊势型1番舰——伊势
图鉴编号：4→1004 稀有度：3→4
CV：N/A  人设：Flaurel
国籍：J国 类型：战列舰→航空战列舰
改造等级：伊势→伊势·改(Lv 20)
耐久  74→79 火力  74(89)→64(84)
装甲  62(82)→67(87) 鱼雷  0(0)→0(0)
回避  22(42)→26(49) 对空  28(58)→43(73)
搭载  12→24 对潜  0(0)→0(0)
航速  24.5→24.5 索敌  10(35)→20(45)
射程  长→长 幸运  15→15
最大消费量
燃料  85→85 弹药  120→120
强化加成
火力  74→32 鱼雷  0→0
装甲  62→67 对空  14→50
改造
0/100/900/400 航母改造核心*4
搭载  装备
3→0
 日本35.6厘米连装炮 → 日本35.6厘米连装炮
3→0
 日本14厘米单装炮 → 对空喷进炮
3→12
未装备→ 瑞云
3→12
未装备
技能（改造后习得）
技能名称  机群驱散B
技能描述  降低航空战时敌方轰炸机10%/15%/20%的命中率
获得方式
建造（4小时30分）
打捞(1-4，2-3后全图，2015春季活动E1～E4)
友情链接：舰少资料库

阅读更多：战舰少女:伊势（https://zh.moegirl.org/%E6%88%98%E8%88%B0%E5%B0%91%E5%A5%B3%3A%E4%BC%8A%E5%8A%BF）
本文引自萌娘百科（https://zh.moegirl.org/），文字内容遵守【知识共享 署名-非商业性使用-相同方式共享 3.0】协议。''')
  ori_lines_list.append('''伊势型2番舰——日向
图鉴编号：5→1005 稀有度：3→4
CV：N/A  人设：Flaurel
国籍：J国 类型：战列舰→航空战列舰
改造等级：日向→日向·改(Lv 20)
耐久  74→79 火力  74(94)→64(84)
装甲  62(82)→67(87) 鱼雷  0(0)→0(0)
回避  22(42)→26(49) 对空  28(58)→43(73)
搭载  12→24 对潜  0(0)→0(0)
航速  24.5→24.5 索敌  10(35)→20(45)
射程  长→长 幸运  15→15
最大消费量
燃料  85→85 弹药  120→120
强化加成
火力  74→32 鱼雷  0→0
装甲  62→67 对空  14→50
改造
0/100/900/400 航母改造核心*4
搭载  装备
3→0
 日本35.6厘米连装炮 → 日本35.6厘米连装炮
3→0
 日本14厘米单装炮 → 对空喷进炮
3→12
未装备→ 瑞云
3→12
未装备
技能（改造后习得）
技能名称  机群驱散T
技能描述  降低航空战时敌方攻击机10%/15%/20%的命中率
获得方式
建造（4小时30分）
打捞(1-4，2-1后除2-4全图
2015春季活动E1～E4)
友情链接：舰少资料库

阅读更多：战舰少女:日向（https://zh.moegirl.org/%E6%88%98%E8%88%B0%E5%B0%91%E5%A5%B3%3A%E6%97%A5%E5%90%91）
本文引自萌娘百科（https://zh.moegirl.org/），文字内容遵守【知识共享 署名-非商业性使用-相同方式共享 3.0】协议。''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  ori_lines_list.append('''''')
  main()
