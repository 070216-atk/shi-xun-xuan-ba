export const stages = [
  { id: 1, name: '集中宣讲', window: '10月29日 14:00', desc: '学院组织统一宣讲，由各实训室介绍项目方向、招录名额、培养安排与考核要求' },
  { id: 2, name: '实训室开放日', window: '10月29日–10月30日', desc: '学生实地走访实训室，与导师、高年级学生交流，了解项目氛围和培养方式' },
  { id: 3, name: '意向填报', window: '11月2日 08:00–12:00', desc: '最多填报3个平行导师意向，不分先后；同时选择是否服从实训室内部调剂和全院跨实训室调剂' },
  { id: 4, name: '导师初选与面试考核', window: '11月2日 12:00–11月3日 17:00', desc: '以实训室为单位统一组织面试，所有报名学生均参加；确定拟录取、递补和内部调剂候选名单' },
  { id: 5, name: '拟录取与学生确认', window: '11月4日 08:00–12:00', desc: '仅1个拟录取意向自动确认；2个及以上须限时自主选择唯一去向；逾期视为放弃，按递补顺位补充' },
  { id: 6, name: '分轮补录', window: '11月4日 12:00–11月5日 17:00', desc: '公布剩余名额，未录取学生重新填报志愿；依次完成第二轮和第三轮补录' },
  { id: 7, name: '统筹调剂录取', window: '11月6日 08:00–12:00', desc: '剩余名额纳入全院调剂池：内部调剂 → 跨实训室调剂 → 约谈未服从调剂学生并统筹安置' },
  { id: 8, name: '结果公示与正式入组', window: '公示1天', desc: '学院汇总最终录取名单并公示1天；无异议后正式加入实训室开展AI+PBL项目实训' }
]

export const labs = [
  {
    id: 'L1', name: '智能软件系统实训室', quota: 12, applied: 0,
    direction: 'AI 应用开发、全栈工程、智能软件系统设计',
    mentors: [{ name: '张建国', title: '副教授', intro: '研究方向：软件工程与智能化开发，主持省级教改项目2项' }, { name: '刘慧', title: '讲师', intro: '研究方向：大模型应用开发，指导学生获省级竞赛一等奖' }],
    training: '第一学期完成 Web 全栈基础训练；第二学期进入 AI+PBL 真实项目组，双周迭代汇报',
    assessment: '出勤与周报 30% + 项目里程碑 40% + 期末答辩 30%',
    location: '实验楼 A301', contact: '张建国 138-0000-0001', openDay: '10月29日 15:30 / 10月30日 09:00'
  },
  {
    id: 'L2', name: '大数据与云计算实训室', quota: 10, applied: 0,
    direction: '数据采集与治理、分布式计算、云平台运维',
    mentors: [{ name: '王志强', title: '教授', intro: '研究方向：分布式系统，与多家企业共建数据平台' }, { name: '陈静', title: '讲师', intro: '研究方向：数据可视化与数据治理' }],
    training: 'Hadoop/Spark 基础训练后进入企业数据项目，参与真实数据管道建设',
    assessment: '实验报告 30% + 项目交付 40% + 答辩 30%',
    location: '实验楼 A302', contact: '王志强 138-0000-0002', openDay: '10月29日 16:00 / 10月30日 10:00'
  },
  {
    id: 'L3', name: '机器视觉实训室', quota: 8, applied: 0,
    direction: '图像识别、目标检测、工业视觉质检',
    mentors: [{ name: '李国栋', title: '副教授', intro: '研究方向：深度学习与计算机视觉，发表SCI论文10余篇' }, { name: '赵雪', title: '讲师', intro: '研究方向：工业缺陷检测' }],
    training: 'PyTorch 基础 + 视觉算法训练，随后进入工业质检 PBL 项目',
    assessment: '算法作业 30% + 项目指标达成 40% + 答辩 30%',
    location: '实验楼 B201', contact: '李国栋 138-0000-0003', openDay: '10月29日 15:00 / 10月30日 14:00'
  },
  {
    id: 'L4', name: '物联网与嵌入式实训室', quota: 10, applied: 0,
    direction: '嵌入式开发、传感器网络、智能硬件',
    mentors: [{ name: '周明', title: '副教授', intro: '研究方向：物联网系统，指导学生获电子设计竞赛国奖' }, { name: '吴倩', title: '讲师', intro: '研究方向：嵌入式AI部署' }],
    training: 'STM32/树莓派基础训练后进入智能硬件 PBL 项目，完成端到端原型',
    assessment: '硬件实验 30% + 原型交付 40% + 答辩 30%',
    location: '实验楼 B202', contact: '周明 138-0000-0004', openDay: '10月30日 09:30 / 10月30日 15:00'
  },
  {
    id: 'L5', name: '网络与信息安全实训室', quota: 8, applied: 0,
    direction: '网络攻防基础、安全运维、密码学应用',
    mentors: [{ name: '郑海', title: '教授', intro: '研究方向：网络空间安全，CTF 战队指导教师' }, { name: '孙丽', title: '讲师', intro: '研究方向：应用安全与渗透测试' }],
    training: '网络安全基础与靶场训练，随后进入安全评估 PBL 项目与 CTF 集训',
    assessment: '靶场成绩 30% + 项目报告 40% + 答辩 30%',
    location: '实验楼 C101', contact: '郑海 138-0000-0005', openDay: '10月29日 16:30 / 10月30日 11:00'
  }
]

export const users = [
  { username: 'student', password: '123456', role: 'student', id: 'S2023001', name: '李明', className: '计科2301' },
  { username: 'student2', password: '123456', role: 'student', id: 'S2023002', name: '王芳', className: '计科2301' },
  { username: 'teacher', password: '123456', role: 'teacher', id: 'T001', name: '张建国', labId: 'L1' },
  { username: 'admin', password: '123456', role: 'admin', id: 'A001', name: '学院管理员' }
]

const surnames = ['李', '王', '张', '刘', '陈', '杨', '赵', '黄', '周', '吴', '徐', '孙', '马', '朱', '胡', '郭', '何', '林', '罗', '郑']
const givens = ['明', '芳', '伟', '娜', '磊', '静', '洋', '艳', '勇', '杰', '涛', '超', '秀英', '霞', '平', '刚', '桂英', '文', '辉', '力']

export const students = Array.from({ length: 60 }, (_, i) => ({
  id: `S2023${String(i + 1).padStart(3, '0')}`,
  name: i === 0 ? '李明' : i === 1 ? '王芳' : surnames[i % 20] + givens[(i * 7 + 3) % 20],
  className: `计科230${(i % 3) + 1}`
}))

export const notices = [
  { title: '关于举行实训室进组集中宣讲的通知', date: '10月27日', content: '学院定于10月29日14:00在学术报告厅举行统一宣讲，各实训室将介绍项目方向、招录名额、培养安排与考核要求，请全体同学参加。' },
  { title: '实训室开放日安排', date: '10月28日', content: '10月29日至30日为实训室开放日，各室具体开放时间见实训室详情页，欢迎实地走访并与导师、高年级学长交流。' },
  { title: '意向填报须知', date: '11月1日', content: '11月2日08:00–12:00开放意向填报。最多填报3个平行导师意向，不分先后；请慎重选择是否服从实训室内部调剂和全院跨实训室调剂。截止后不可修改。' }
]