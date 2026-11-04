<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch">
      <el-form-item label="聊天对象" prop="name">
        <el-input v-model="queryParams.name" placeholder="群名或好友名" clearable style="width:180px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="消息内容" prop="keyword">
        <el-input v-model="queryParams.keyword" placeholder="正文或异常原因" clearable style="width:180px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="分类" prop="msgCategories">
        <el-select v-model="queryParams.msgCategories" multiple collapse-tags clearable placeholder="全部" style="width:180px">
          <el-option v-for="item in categories" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="分类状态" prop="classifyStatus">
        <el-select v-model="queryParams.classifyStatus" clearable placeholder="全部" style="width:120px">
          <el-option v-for="item in classifyStatuses" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="录入状态" prop="entryStatus">
        <el-select v-model="queryParams.entryStatus" clearable placeholder="全部" style="width:130px">
          <el-option v-for="item in entryStatuses" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="任务状态" prop="taskStatus">
        <el-select v-model="queryParams.taskStatus" clearable placeholder="全部" style="width:130px">
          <el-option v-for="item in taskStatuses" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="创建时间">
        <el-date-picker v-model="dateRange" type="daterange" value-format="yyyy-MM-dd" range-separator="-" start-placeholder="开始日期" end-placeholder="结束日期" style="width:250px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
        <el-popover placement="bottom-start" width="680" trigger="hover" popper-class="wechat-help-popover">
          <div class="wechat-help">
            <div class="help-title">菜单按钮说明</div>
            <ul>
              <li><b>自动分类</b>：关闭时只在勾选消息后手动分类；开启后由后端定时扫描未分类消息。</li>
              <li><b>自动录入</b>：关闭时仅在勾选消息后手动录入；开启后由“微信自动化-INQ/OFFER录入”定时任务按配置扫描并调用 AI 录入。</li>
              <li><b>拉取</b>：立即从微信本地库同步消息入库；聊天对象有值时只拉该会话，留空拉全部。</li>
              <li><b>分类选中</b>：对勾选消息强制重新调用 AI 分类为 INQ/OFFER/TRACE/OTHER，单次最多 20 条；已录入或已生成任务的消息不会被覆盖。同一发送人连续补充消息按整组语义归并，INQ/OFFER 的前序误判会自动修正，不判 TRACE。</li>
              <li><b>录入选中</b>：对勾选消息执行 AI 录入和料号匹配；仅处理已成功分类的 INQ/OFFER。选中一条会自动扩展为同一发送人 30 秒内的连续消息组，并带上被引用的原文，整组只调用一次 AI 录入，已录入消息幂等跳过。</li>
              <li><b>重排选中</b>：把失败、跳过或达到重试上限的录入消息重新放回队列，不立即执行。</li>
              <li><b>TRACE补扫</b>：补扫近期已关联 INQ/OFFER 且尚未成功发送的 TRACE 飞书预警，已预警消息不重复发送。</li>
              <li><b>清分类缓存</b>：只清空 AI 分类缓存，不改历史分类结果；调整提示词后使用。</li>
              <li><b>列表分类下拉</b>：人工修改单条消息分类；我方成员消息固定 OTHER，不可改；已录入或已生成任务的消息也锁定。</li>
            </ul>
            <div class="help-title">分类规则</div>
            <ul>
              <li><b>INQ 询价</b>：任何一方询问具体型号、料号、规格、品类、价格、库存、可供数量、交期、新旧或能否供货；带目标价仍是 INQ。只是打听历史成交价（“多少钱拿的”）或问对方是否还收货（“你们还收不”），没有具体待询价对象的，判 OTHER。</li>
              <li><b>OFFER 报价</b>：对 INQ 的报价或供货回应，或供应商主动报价/价目表；外部只回复我方询问的料号、型号也按 OFFER 处理。</li>
              <li><b>缺货/无库存</b>：“缺货、没货、无货、没有、no stock、out of stock、unavailable”等明确否定供货的消息由规则直接判为 OTHER，不调用 AI，也不录入 Offer；混合消息中无报价的缺货物料行也会在录入时剔除。</li>
              <li><b>TRACE 二次跟进</b>：围绕已有 INQ/OFFER 追问或确认包装、原装、新旧、COO、DC、质保、交期、数量、价格、下单等；不一定是疑问句，但必须有上下文指向。</li>
              <li><b>OTHER 其他</b>：寒暄、致谢、收到、OK、check、checking、稍等、纯表情、广告、系统消息，以及没有推进业务的陈述。</li>
              <li><b>连续消息组</b>：同一发送人相邻消息间隔不超过 <code>offerEntryGroupGapSec</code> 时按整组判断：有明确询价且无供货条件时整组 INQ；有报价/库存/现货等供货信息且无明确询价问句时整组 OFFER。型号清单、数量、库存等补充消息会随整组修正，不再沿用第一条的误判。</li>
              <li><b>引用补充合并</b>：同一发送人针对同一条消息的补充回复，即使超过 30 秒，也会在 <code>offerEntryQuoteLinkSec</code>（默认 1800 秒）内合并后一次录入，避免“一条有价格无料号、一条有料号无价格”。</li>
              <li><b>30 秒规则来源</b>：该间隔来自字典 <code>wechat_analysis_config.offerEntryGroupGapSec</code>，默认 30 秒，可自行修改；不是代码写死。分类整组归并和录入消息聚合都读取同一个配置。配置位置：系统管理 → 字典管理 → 字典类型 <code>wechat_analysis_config</code>。</li>
              <li>买卖双向，不按“客户/供应商”身份直接判类；我方成员发送的待判消息固定为 OTHER/SELF，不调用 AI。</li>
              <li>我方历史消息在提示词中标记为 SELF-OTHER，仍作为业务上下文，可供后续外部消息关联 inq/offer。</li>
              <li>我方成员发送的消息固定为 OTHER，不调用 AI 分类；该名单由 <code>selfNames</code> 配置，默认 Eke,May,Jin,Jasmine。</li>
              <li><b>过滤顺序</b>：blackNames / pollIgnoreChats 命中 → 转黑表；whiteNames 非空时只分类白名单会话；selfNames 直接 OTHER；其余才调用 AI。</li>
              <li><b>人工改分类</b>：下拉修改会写入 MANUAL 来源，可正常参与后续录入和任务生成；已录入或已生成任务的消息会锁定，避免破坏已有记录。</li>
            </ul>
            <div class="help-title">TRACE 告警规则</div>
            <ul>
              <li><b>触发条件</b>：<code>traceAlertEnabled=true</code>；消息分类为 TRACE、来源为 LLM/CACHE/MANUAL；发送人不在 <code>selfNames</code>；并且已关联 INQ 或 OFFER。无关联的 TRACE 不告警。</li>
              <li><b>6 小时时效</b>：只处理消息发送时间在 <code>traceAlertMaxAgeMin</code>（当前 360 分钟）内的 TRACE。超过 6 小时的历史消息即使尚未告警也会跳过。</li>
              <li><b>同一条永久去重</b>：成功告警后写入 <code>trace_alerted_at</code>，后续扫描不再重复发送；发送失败写入 <code>trace_alert_error</code>，保留为候选并在下一轮补扫重试。</li>
              <li><b>同会话 5 分钟冷却</b>：同一聊天对象成功告警后的 <code>traceAlertChatCooldownMin</code> 分钟（当前 5 分钟）内，其他 TRACE 不再发送飞书，并写入 <code>trace_alert_suppressed_at</code>；冷却结束后也不会补发这些被跳过的消息。配置为 0 表示关闭冷却。</li>
              <li><b>补扫频率与批量</b>：定时任务默认每 30 秒执行一次，每次最多处理 <code>traceAlertScanBatchSize</code> 条（当前 50 条）；也可点击本页“TRACE补扫”手动触发。</li>
              <li><b>配置位置</b>：系统管理 → 字典管理 → 字典类型 <code>wechat_analysis_config</code>，可修改 <code>traceAlertEnabled</code>、<code>traceAlertMaxAgeMin</code>、<code>traceAlertChatCooldownMin</code>、<code>traceAlertScanBatchSize</code>。</li>
              <li><b>定时任务位置</b>：系统监控 → 定时任务 → 任务组 <code>WECHAT</code> → <code>微信自动化-TRACE预警</code>；可在此启停、修改 cron 和查看调度日志。</li>
              <li><b>飞书机器人</b>：应用凭证位于 <code>ruoyi-admin/src/main/resources/application.yml</code> 的 <code>feishu.app-id</code>、<code>feishu.app-secret</code>；生产环境可用环境变量 <code>FEISHU_APP_SECRET</code> 注入密钥。</li>
            </ul>
            <div class="help-title">多消息聚合录入</div>
            <ul>
              <li><b>聚合范围</b>：同一会话 + 同一发送人，相邻消息时间差不超过 <code>offerEntryGroupGapSec</code>（默认 30 秒）时视为一组；回复同一条引用消息的补充内容，在 <code>offerEntryQuoteLinkSec</code>（默认 1800 秒）内也会并入同组；不同发送人、不同会话不会并入同一组。</li>
              <li><b>会话上下文补全</b>：OFFER 只有价格、没有料号时（例如对方单独回复“2800，2450”），会取同一会话最近 <code>aiContextWindow</code> 条消息（默认 20 条，与 AI 分类使用同一个配置）交给 AI 匹配料号，不再只看紧邻的一两条；上下文消息本身不生成物料行，也不改变自己的录入状态。</li>
              <li><b>群映射</b>：聊天对象名称与供应商档案的 <code>sales_group / purchase_group</code> 做精确匹配；匹配前会去除首尾空白、把制表符和连续空格折叠为一个空格，忽略英文大小写。名称内容不同（例如多了文字或符号）仍需在供应商档案补全群名。</li>
              <li><b>等待稳定</b>：组内最新一条消息距今不足 <code>offerEntryMinAgeSec</code>（默认 30 秒）时先不录入，避免报价/交期还在继续补充；返回结果里的“等待补充”即此情况。</li>
              <li><b>引用原文</b>：消息的 <code>relat_inq</code>、<code>relat_offer</code> 或 <code>quote</code> 指向的原消息会作为料号上下文一并交给 AI；例如 Johnny 回复 18400USD 并补充 2weeks，会连同被引用的 May 的 INQ 一起解析。</li>
              <li><b>一次录入</b>：整组只调用一次 AI 录入。组内除主消息外的 INQ/OFFER 也会标记录入成功，但任务状态记为 <code>SKIPPED</code>（MERGED_ENTRY），避免重复生成任务。</li>
              <li><b>空料号拦截</b>：AI 返回的物料行没有料号时不入库，并写入需人工处理的录入原因，避免生成无法匹配、无法生成任务的空料号报价记录。</li>
              <li><b>纯价格 OFFER</b>：例如 594955“@朝阳 3475X, 3465x ultra 5 225f”后，594956 仅回复“2800，2450”；录入时会把该会话最近 20 条消息一起作为上下文，生成 3475X=2800、3465X=2450 两条报价，历史上下文不重复生成物料行。</li>
              <li><b>例子</b>：539167（Intel 6346 有货吗）+ 539168（紧接着补充）合并为一个 INQ；539379（被引用的询价）+ 543397（18400USD）+ 543398（2weeks）合并为一个 OFFER。</li>
            </ul>
            <div class="help-title">配置位置</div>
            <ul>
              <li><b>统一配置</b>：系统管理 → 字典管理 → 字典类型 <code>wechat_analysis_config</code>。</li>
              <li><b>blackNames / pollIgnoreChats</b>：聊天对象名称包含任一关键字（逗号分隔、不区分大小写）时，消息转存 <code>wechat_message_black</code>，不进入主表和分类。</li>
              <li><b>whiteNames</b>：非空时只有名称完全匹配的聊天对象参与分类；<b>selfNames</b>：我方成员名单，默认 Eke,May,Jin,Jasmine。</li>
              <li><b>llmPromptBase / llmExamples</b>：系统提示词和示例；<b>aiContextWindow</b> 控制最近上下文条数，<b>llmBatchSize</b> 控制单批条数。</li>
              <li><b>classifyEnabled</b>：自动分类开关；<b>classifyTriggerCount</b>、<b>classifyMaxIdleSec</b>、<b>classifyBatchSize</b> 控制定时触发。</li>
              <li><b>offerEntryEnabled</b>：INQ/OFFER 自动录入总开关。关闭时“微信自动化-INQ/OFFER录入”定时任务不会查询待录入消息；开启后按 <b>offerEntryScanSec</b> 扫描、每批最多 <b>offerEntryBatchSize</b> 条，并按消息时间从早到晚排队。</li>
              <li><b>autoTaskGenerateEnabled</b>：自动任务生成开关。关闭时消息录入成功后仍会保持 <code>task_status=PENDING</code>；任务状态旁的感叹号会显示该原因，可到“微信询报价任务”页面手动点击“生成任务”。</li>
              <li><b>offerEntryMinAgeSec</b>：录入前消息稳定等待秒数（默认 30）；<b>offerEntryGroupGapSec</b>：同一发送人连续消息的整组分类与录入聚合间隔秒数（默认 30）；<b>aiContextWindow</b>：纯价格 OFFER 录入和 AI 分类共同使用的最近消息条数（默认 20）；<b>offerEntryQuoteLinkSec</b>：同一引用消息的补充回复聚合窗口秒数（默认 1800）；<b>offerEntryMaxAgeHours</b>：自动录入最大回溯小时数。</li>
              <li><b>录入异常排查</b>：待录入查询失败会写入“系统监控 → 调度日志”的异常信息；单条消息的 AI 录入失败原因见列表的“录入状态/结果”。</li>
              <li><b>traceAlertEnabled</b>：TRACE 告警开关；<b>traceAlertMaxAgeMin</b> 最大消息年龄（默认 360 分钟，即只告警最近 6 小时），<b>traceAlertChatCooldownMin</b> 同群冷却分钟数。</li>
              <li><b>AI 接口</b>：系统管理 → 参数设置中的 <code>sys.ai.enabled</code>、<code>sys.ai.base.url</code>、<code>sys.ai.api.key</code>、<code>sys.ai.model</code>。</li>
              <li><b>录入耗时保护</b>：<code>sys.ai.timeout.ms</code> 控制单次模型调用超时（当前 120000 毫秒，即 2 分钟）；网络超时不会在同一渠道反复重试，避免一条消息长时间占住自动录入任务。</li>
            </ul>
            <div class="help-title">操作提示</div>
            <ul>
              <li>调整提示词后先“清分类缓存”，再勾选消息“分类选中”，否则可能命中旧缓存。</li>
              <li>连续消息聚合是对提示词分类的兜底：即使分类把补充消息判成 TRACE，录入时仍会按同一发送人时间窗合并，不会把 OFFER 拆成两条。</li>
              <li>下拉人工改分类不会自动补关联；需要 TRACE 告警时请确保已有 INQ/OFFER 关联。</li>
            </ul>
          </div>
          <el-button slot="reference" icon="el-icon-question" size="mini">说明</el-button>
        </el-popover>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="3" class="auto-classify-switch">
        <span class="auto-classify-label">自动分类</span>
        <el-switch v-model="autoClassifyEnabled" :disabled="autoClassifySaving" active-color="#13ce66" v-hasPermi="['wechat:message:classify']" @change="handleAutoClassifyChange" />
      </el-col>
      <el-col :span="3" class="auto-classify-switch">
        <span class="auto-classify-label">自动录入</span>
        <el-switch v-model="autoEntryEnabled" :disabled="autoEntrySaving" active-color="#13ce66" v-hasPermi="['wechat:message:entry']" @change="handleAutoEntryChange" />
      </el-col>
      <el-col :span="1.5"><el-button type="primary" plain icon="el-icon-download" size="mini" :loading="polling" @click="handlePoll" v-hasPermi="['wechat:message:poll']">拉取</el-button></el-col>
      <el-col :span="1.5"><el-button type="warning" plain icon="el-icon-magic-stick" size="mini" :loading="classifying" @click="handleClassifySelected" v-hasPermi="['wechat:message:classify']">分类选中</el-button></el-col>
      <el-col :span="1.5"><el-button type="success" plain icon="el-icon-document-add" size="mini" :loading="entering" @click="handleEntrySelected" v-hasPermi="['wechat:message:entry']">录入选中</el-button></el-col>
      <el-col :span="1.5"><el-button type="info" plain icon="el-icon-refresh-left" size="mini" @click="handleRequeue" v-hasPermi="['wechat:message:entry']">重排选中</el-button></el-col>
      <el-col :span="1.5"><el-button type="danger" plain icon="el-icon-bell" size="mini" @click="handleTraceScan" v-hasPermi="['wechat:message:classify']">TRACE补扫</el-button></el-col>
      <el-col :span="1.5"><el-button plain icon="el-icon-delete-solid" size="mini" @click="handleClearCache" v-hasPermi="['wechat:message:classify']">清分类缓存</el-button></el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" />
    </el-row>

    <el-alert v-if="statsText" :title="statsText" type="info" :closable="false" show-icon class="mb8" />
    <el-alert v-if="entryNoticeText" :title="entryNoticeText" :type="entryNoticeType" :closable="false" show-icon class="mb8" />

    <el-table ref="messageTable" v-loading="loading" :data="messageList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="46" />
      <el-table-column label="消息时间" prop="createTime" width="158" />
      <el-table-column label="聊天对象" prop="name" min-width="170" show-overflow-tooltip />
      <el-table-column label="发言人" prop="sender" width="110" show-overflow-tooltip />
      <el-table-column label="分类" width="118" align="center">
        <template slot-scope="scope">
          <el-select v-model="scope.row.msgCategory" size="mini" class="category-select"
                     :disabled="!categoryEditable(scope.row)" @change="value => handleCategoryChange(scope.row, value)">
            <el-option v-for="item in categories" :key="item" :label="item" :value="item" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="正文" min-width="300">
        <template slot-scope="scope">
          <el-tooltip placement="top-start" effect="dark" :open-delay="300" popper-class="wechat-message-tooltip">
            <div slot="content" class="message-tooltip-content">
              <div class="message-tooltip-label">正文</div>
              <div class="message-tooltip-text">{{ displayContent(scope.row) || '（空消息）' }}</div>
              <div v-if="scope.row.quote" class="message-tooltip-quote">
                <div class="message-tooltip-label">引用{{ quoteDisplayKind(scope.row) }}</div>
                <div v-if="isQuoteImage(scope.row)" class="message-tooltip-media">
                  <img v-if="quoteMediaUrl(scope.row)" :src="quoteMediaUrl(scope.row)" class="quote-hover-image" alt="引用图片">
                  <span v-else class="message-tooltip-muted">{{ quoteMediaError(scope.row) || '引用图片加载中…' }}</span>
                </div>
                <div v-if="quoteDisplayText(scope.row)" class="message-tooltip-text">{{ quoteDisplayText(scope.row) }}</div>
                <div v-if="isQuoteFile(scope.row)" class="message-tooltip-file">
                  <i class="el-icon-document"></i>
                  <span>{{ quoteFileName(scope.row) || '引用文件' }}</span>
                  <span class="message-tooltip-muted">详情可打开、下载</span>
                </div>
                <div v-if="isQuotePlaceholder(scope.row) && quoteInfoLoading(scope.row)" class="message-tooltip-muted">正在读取引用详情…</div>
                <div v-if="quoteInfoError(scope.row)" class="message-tooltip-muted">{{ quoteInfoError(scope.row) }}</div>
              </div>
            </div>
            <div class="message-hover">
              <div class="message-content">
                <el-link v-if="isImage(scope.row)" type="primary" :underline="false" @click="showDetail(scope.row)">
                  <i class="el-icon-picture-outline"></i> 查看图片
                </el-link>
                <el-link v-else-if="hasFile(scope.row)" type="primary" :underline="false" @click="showDetail(scope.row)">
                  <i class="el-icon-document"></i> {{ cardTitle(scope.row.content) || '查看文件' }}
                </el-link>
                <span v-else>{{ displayContent(scope.row) }}</span>
              </div>
              <div v-if="scope.row.quote" class="quote-preview">
                <span class="quote-preview-label">引用{{ quoteDisplayKind(scope.row) }}：</span>{{ quoteSummary(scope.row) }}
              </div>
            </div>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="分类状态" width="125" align="center">
        <template slot-scope="scope">
          <el-tag size="mini" :type="statusType(scope.row.classifyStatus)">{{ scope.row.classifyStatus || '-' }}</el-tag>
          <el-tooltip v-if="scope.row.categoryReason" :content="scope.row.categoryReason" placement="top"><i class="el-icon-warning-outline button-help-icon" /></el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="录入状态" width="120" align="center">
        <template slot-scope="scope">
          <el-tag size="mini" :type="statusType(scope.row.entryStatus)">{{ scope.row.entryStatus || '-' }}</el-tag>
          <el-tooltip v-if="scope.row.entryMessage" :content="scope.row.entryMessage" placement="top"><i class="el-icon-warning-outline" /></el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="任务状态" width="118" align="center">
        <template slot-scope="scope">
          <el-tag size="mini" :type="statusType(scope.row.taskStatus)">{{ scope.row.taskStatus || '-' }}</el-tag>
          <el-tooltip v-if="taskStateReason(scope.row)" :content="taskStateReason(scope.row)" placement="top">
            <i class="button-help-icon" :class="taskStateReasonDanger(scope.row) ? 'el-icon-error text-danger' : 'el-icon-warning-outline'" />
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="任务失败原因" min-width="180" show-overflow-tooltip>
        <template slot-scope="scope">
          <span v-if="scope.row.taskFailureReason" class="text-danger">{{ scope.row.taskFailureReason }}</span>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="TRACE预警" width="135" align="center">
        <template slot-scope="scope">
          <span v-if="scope.row.traceAlertedAt">{{ scope.row.traceAlertedAt }}</span>
          <el-tooltip v-else-if="scope.row.traceAlertSuppressedAt" :content="'同会话预警冷却跳过：' + scope.row.traceAlertSuppressedAt" placement="top">
            <span class="text-info">冷却跳过</span>
          </el-tooltip>
          <span v-else-if="scope.row.traceAlertError" class="text-danger">{{ scope.row.traceAlertError }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" align="center" fixed="right">
        <template slot-scope="scope"><el-button size="mini" type="text" icon="el-icon-view" @click="showDetail(scope.row)">详情</el-button></template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />

    <el-dialog title="微信消息详情" :visible.sync="detailOpen" width="960px" append-to-body @closed="closeDetail">
      <el-form label-width="110px" size="small">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="聊天对象">{{ detail.name }}</el-form-item></el-col>
          <el-col :span="12"><el-form-item label="发言人">{{ detail.sender }}</el-form-item></el-col>
          <el-col :span="12"><el-form-item label="消息时间">{{ detail.createTime }}</el-form-item></el-col>
          <el-col :span="12"><el-form-item label="消息ID">{{ detail.id || '-' }}</el-form-item></el-col>
          <el-col :span="12"><el-form-item label="消息键">{{ detail.username }}:{{ detail.localId }}</el-form-item></el-col>
          <el-col :span="12"><el-form-item label="消息类型">{{ detail.type || '-' }}</el-form-item></el-col>
          <el-col :span="12"><el-form-item label="分类">{{ detail.msgCategory }} / {{ detail.categorySource }}</el-form-item></el-col>
          <el-col :span="12"><el-form-item label="分类状态">{{ detail.classifyStatus }}</el-form-item></el-col>
          <el-col :span="12"><el-form-item label="录入状态">{{ detail.entryStatus }}</el-form-item></el-col>
          <el-col :span="12"><el-form-item label="任务状态">{{ detail.taskStatus }}</el-form-item></el-col>
        </el-row>
        <el-form-item v-if="taskStateReason(detail)" :label="detail.taskStatus === 'PENDING' ? '任务待处理原因' : '任务处理说明'">
          <div class="detail-text" :class="taskStateReasonDanger(detail) ? 'text-danger' : ''">{{ taskStateReason(detail) }}</div>
        </el-form-item>
        <el-form-item label="正文"><div class="detail-text">{{ displayContent(detail) }}</div></el-form-item>
        <el-form-item v-if="detail.quote" label="引用内容">
          <div class="quote-box">
            <span class="quote-kind">
              <i :class="quoteKindIcon(quoteDisplayKind(detail))"></i>
              {{ quoteDisplayKind(detail) }}
            </span>
            <div v-if="isQuoteImage(detail)" v-loading="detailQuoteMediaLoading" class="quote-image-panel">
              <el-image v-if="detailQuoteMediaUrl" :src="detailQuoteMediaUrl" :preview-src-list="[detailQuoteMediaUrl]" fit="contain" class="quote-detail-image" />
              <div v-else-if="detailQuoteMediaError" class="media-error">
                {{ detailQuoteMediaError }}
                <el-button type="text" @click="loadDetailQuoteMedia(detail)">重试</el-button>
              </div>
              <div v-if="detailQuoteMediaUrl" class="media-actions">
                <el-button size="mini" type="primary" icon="el-icon-download" :loading="mediaDownloading" @click="downloadQuoteMedia(detail)">下载图片</el-button>
              </div>
            </div>
            <div v-if="isQuoteFile(detail)" class="file-card quote-file-card">
              <i class="el-icon-document file-icon"></i>
              <span class="file-name">{{ quoteFileName(detail) || '引用文件' }}</span>
              <el-button size="mini" type="primary" plain icon="el-icon-view" @click="openQuoteFilePreview(detail)">打开</el-button>
              <el-button size="mini" type="primary" icon="el-icon-download" :loading="mediaDownloading" @click="downloadQuoteMedia(detail)">下载</el-button>
            </div>
            <div v-if="quoteDisplayText(detail)" class="detail-text">{{ quoteDisplayText(detail) }}</div>
            <div v-if="isQuotePlaceholder(detail)" class="quote-empty">
              {{ quoteInfoLoading(detail) ? '正在读取引用详情…' : (quoteInfoError(detail) || '引用的' + quoteDisplayKind(detail) + '未包含可展示内容') }}
            </div>
          </div>
        </el-form-item>
        <el-form-item v-if="isImage(detail)" label="图片">
          <div v-loading="detailMediaLoading" class="media-panel">
            <el-image v-if="detailMediaUrl" :src="detailMediaUrl" :preview-src-list="[detailMediaUrl]" fit="contain" class="image-preview" />
            <div v-else-if="detailMediaError" class="media-error">
              {{ detailMediaError }}
              <el-button type="text" @click="loadImagePreview(detail)">重试</el-button>
            </div>
            <div class="media-actions">
              <el-button size="mini" type="primary" icon="el-icon-download" :disabled="!detailMediaUrl" :loading="mediaDownloading" @click="downloadMedia(detail)">下载图片</el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item v-if="hasFile(detail)" label="文件">
          <div class="file-card">
            <i class="el-icon-document file-icon"></i>
            <span class="file-name">{{ cardTitle(detail.content) || '微信文件' }}</span>
            <el-button size="mini" type="primary" plain icon="el-icon-view" @click="openFilePreview(detail)">打开</el-button>
            <el-button size="mini" type="primary" icon="el-icon-download" :loading="mediaDownloading" @click="downloadMedia(detail)">下载</el-button>
          </div>
        </el-form-item>
        <el-form-item label="分类说明"><div class="detail-text">{{ detail.categoryReason || '-' }}</div></el-form-item>
        <el-form-item label="录入说明"><div class="detail-text">{{ detail.entryMessage }}</div></el-form-item>
        <el-form-item label="任务说明"><div class="detail-text">{{ detail.taskMessage }}</div></el-form-item>
        <el-form-item v-if="detail.relatInq" label="关联 INQ"><pre class="json-box">{{ pretty(detail.relatInq) }}</pre></el-form-item>
        <el-form-item v-if="detail.relatOffer" label="关联 OFFER"><pre class="json-box">{{ pretty(detail.relatOffer) }}</pre></el-form-item>
      </el-form>
    </el-dialog>

    <el-dialog :title="filePreviewName ? ('文件预览 - ' + filePreviewName) : '文件预览'"
               :visible.sync="filePreviewOpen" width="82%" top="5vh" append-to-body @closed="clearFilePreview">
      <div v-loading="filePreviewLoading" class="file-preview">
        <div v-if="filePreviewError" class="media-error">{{ filePreviewError }}</div>
        <iframe v-else-if="filePreviewKind === 'pdf'" :src="filePreviewUrl" class="pdf-preview" />
        <pre v-else-if="filePreviewKind === 'text'" class="text-preview">{{ filePreviewText }}</pre>
        <div v-else-if="filePreviewKind === 'excel'" class="excel-preview">
          <div v-if="filePreviewNote" class="sheet-note">{{ filePreviewNote }}</div>
          <table class="excel-table">
            <tbody>
              <tr v-for="(row, rowIndex) in filePreviewRows" :key="rowIndex">
                <th>{{ rowIndex + 1 }}</th>
                <td v-for="(cell, colIndex) in row" :key="colIndex">{{ cell }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div slot="footer">
        <el-button size="small" type="primary" icon="el-icon-download" :loading="mediaDownloading" @click="downloadFilePreview">下载原文件</el-button>
        <el-button size="small" @click="filePreviewOpen = false">关闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { saveAs } from 'file-saver'
import * as XLSX from 'xlsx'
import { listWechatMessages, getWechatMessageStats, getWechatMedia, getWechatQuoteInfo, getWechatQuoteMedia, runWechatClassify, updateWechatMessageCategory, setWechatClassifyConfig, clearWechatClassifyCache, scanWechatTrace, setWechatEntryConfig, runWechatEntry, requeueWechatEntry, runWechatPoll } from '@/api/crm/wechatMessage'

const DOCUMENT_EXTS = ['pdf', 'xls', 'xlsx', 'csv', 'txt', 'md', 'json', 'xml', 'log', 'doc', 'docx', 'ppt', 'pptx', 'jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp']
const TEXT_EXTS = ['txt', 'md', 'json', 'xml', 'log']
const MESSAGE_FIELD_ALIASES = {
  msg_category: 'msgCategory',
  category_source: 'categorySource',
  category_reason: 'categoryReason',
  entry_fail_count: 'entryFailCount',
  entry_status: 'entryStatus',
  entry_message: 'entryMessage',
  entry_offer_count: 'entryOfferCount',
  entry_offer_ids: 'entryOfferIds',
  entry_at: 'entryAt',
  classify_status: 'classifyStatus',
  classify_at: 'classifyAt',
  classify_fail_count: 'classifyFailCount',
  task_status: 'taskStatus',
  task_at: 'taskAt',
  task_message: 'taskMessage',
  task_failure_reason: 'taskFailureReason',
  trace_alerted_at: 'traceAlertedAt',
  trace_alert_error: 'traceAlertError',
  trace_alert_suppressed_at: 'traceAlertSuppressedAt',
  relat_inq: 'relatInq',
  relat_offer: 'relatOffer'
}
const IMAGE_MIME_EXTS = {
  'image/jpeg': 'jpg',
  'image/png': 'png',
  'image/gif': 'gif',
  'image/webp': 'webp',
  'image/bmp': 'bmp'
}

export default {
  name: 'CrmWechatMessage',
  data() {
    return {
      loading: false,
      polling: false,
      classifying: false,
      autoClassifyEnabled: false,
      autoClassifySaving: false,
      autoEntryEnabled: false,
      autoEntrySaving: false,
      autoTaskGenerateEnabled: false,
      entering: false,
      showSearch: true,
      total: 0,
      messageList: [],
      selected: [],
      dateRange: [],
      statsText: '',
      entryNoticeText: '',
      entryNoticeType: 'warning',
      detailOpen: false,
      detail: {},
      detailMediaUrl: '',
      detailMediaLoading: false,
      detailMediaError: '',
      detailQuoteMediaUrl: '',
      detailQuoteMediaLoading: false,
      detailQuoteMediaError: '',
      quoteInfoMap: {},
      quoteMediaMap: {},
      quoteInfoLoadingMap: {},
      quoteInfoErrorMap: {},
      quoteMediaErrorMap: {},
      mediaDownloading: false,
      filePreviewOpen: false,
      filePreviewRow: {},
      filePreviewQuote: false,
      filePreviewName: '',
      filePreviewLoading: false,
      filePreviewError: '',
      filePreviewKind: '',
      filePreviewUrl: '',
      filePreviewText: '',
      filePreviewRows: [],
      filePreviewNote: '',
      categories: ['INQ', 'OFFER', 'TRACE', 'OTHER'],
      classifyStatuses: ['PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'SKIPPED'],
      entryStatuses: ['NOT_APPLICABLE', 'PENDING', 'PROCESSING', 'SUCCESS', 'SUCCESS_WARN', 'FAILED', 'DEAD', 'SKIPPED'],
      taskStatuses: ['PENDING', 'PROCESSING', 'GENERATED', 'SKIPPED', 'MANUAL_REVIEW', 'FAILED'],
      queryParams: { pageNum: 1, pageSize: 20, name: undefined, keyword: undefined, msgCategories: [], classifyStatus: undefined, entryStatus: undefined, taskStatus: undefined, beginTime: undefined, endTime: undefined }
    }
  },
  created() {
    this.dateRange = this.last30Days()
    this.handleQuery()
    this.loadStats()
  },
  beforeDestroy() {
    this.releaseQuotePreviewMedia()
    this.releaseDetailQuoteMedia()
  },
  methods: {
    last30Days() {
      const fmt = date => {
        const y = date.getFullYear()
        const m = String(date.getMonth() + 1).padStart(2, '0')
        const d = String(date.getDate()).padStart(2, '0')
        return y + '-' + m + '-' + d
      }
      const now = new Date()
      return [fmt(new Date(now.getTime() - 29 * 86400000)), fmt(now)]
    },
    getList() {
      this.loading = true
      const query = Object.assign({}, this.queryParams)
      query.msgCategories = (this.queryParams.msgCategories || []).join(',')
      if (this.dateRange && this.dateRange.length === 2) {
        query.beginTime = this.dateRange[0] + ' 00:00:00'
        query.endTime = this.dateRange[1] + ' 23:59:59'
      }
      listWechatMessages(query).then(res => {
        this.messageList = (res.rows || []).map(this.normalizeMessage)
        this.total = res.total || 0
        this.releaseQuotePreviewMedia()
        this.loadQuoteDetails(this.messageList)
      }).finally(() => { this.loading = false })
    },
    normalizeMessage(row) {
      const data = Object.assign({}, row)
      Object.keys(MESSAGE_FIELD_ALIASES).forEach(key => {
        const field = MESSAGE_FIELD_ALIASES[key]
        if (data[field] === undefined) {
          data[field] = data[key]
        }
      })
      data._savedMsgCategory = data.msgCategory
      return data
    },
    loadStats() {
      getWechatMessageStats().then(res => {
        const data = res.data || {}
        const poll = data.poll || {}
        const entry = data.entry || {}
        const task = data.task || {}
        const pending = Number(entry.pendingCnt || 0)
        const batchSize = Number(entry.batchSize || 0)
        const scanSec = Number(entry.scanSec || 0)
        this.autoClassifyEnabled = !!poll.classifyEnabled
        this.autoEntryEnabled = !!entry.enabled
        this.autoTaskGenerateEnabled = !!task.enabled
        this.statsText = (poll.classifyEnabled ? '自动分类已开启' : '自动分类已关闭') + '，' +
          (entry.enabled ? '自动录入已开启' : '自动录入已关闭') + '，未分类 ' + (poll.unclassifiedCnt || 0) +
          ' 条，待录入 ' + pending + ' 条，最近拉取 ' + (poll.lastRunAt || '-')
        this.entryNoticeType = entry.enabled ? 'info' : 'warning'
        if (!entry.enabled) {
          this.entryNoticeText = '自动录入定时任务未执行：offerEntryEnabled=false' +
            (pending > 0 ? '，当前 ' + pending + ' 条已分类 INQ/OFFER 不会自动录入' : '') +
            '。配置位置：系统管理 → 字典管理 → wechat_analysis_config。'
        } else if (pending > 0) {
          this.entryNoticeText = '自动录入已开启：待录入 ' + pending + ' 条；每 ' + scanSec +
            ' 秒扫描一次，每批最多 ' + batchSize + ' 条，按消息时间从早到晚处理。'
        } else {
          this.entryNoticeText = ''
        }
      }).catch(error => {
        this.entryNoticeType = 'error'
        this.entryNoticeText = '微信自动化状态读取失败：' + (error.message || '未知错误')
      })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
      this.loadStats()
    },
    resetQuery() {
      this.resetForm('queryForm')
      this.queryParams.msgCategories = []
      this.dateRange = this.last30Days()
      this.handleQuery()
    },
    handleSelectionChange(rows) {
      this.selected = rows || []
    },
    selectedKeys() {
      return this.selected.map(row => row.username + ':' + row.localId)
    },
    requireSelection() {
      if (!this.selected.length) {
        this.$modal.msgWarning('请先勾选消息')
        return false
      }
      if (this.selected.length > 20) {
        this.$modal.msgWarning('单次最多选择20条消息')
        return false
      }
      return true
    },
    handlePoll() {
      this.polling = true
      runWechatPoll({ chat: this.queryParams.name || null }).then(res => {
        this.$modal.msgSuccess((res.data && res.data.message) || '拉取完成')
        this.getList()
      }).finally(() => { this.polling = false })
    },
    handleClassifySelected() {
      if (!this.requireSelection()) return
      this.classifying = true
      runWechatClassify({ keys: this.selectedKeys() }).then(res => {
        this.$modal.msgSuccess((res.data && res.data.message) || '分类完成')
        this.getList()
      }).finally(() => { this.classifying = false })
    },
    categoryEditable(row) {
      if (!row) return false
      if (String(row.categorySource || '').toUpperCase() === 'SELF') return false
      const entry = String(row.entryStatus || '').toUpperCase()
      const task = String(row.taskStatus || '').toUpperCase()
      return !['PROCESSING', 'SUCCESS', 'SUCCESS_WARN'].includes(entry) && !['PROCESSING', 'GENERATED'].includes(task)
    },
    handleCategoryChange(row, category) {
      const old = row._savedMsgCategory
      if (!category || category === old) return
      updateWechatMessageCategory({ username: row.username, localId: row.localId, category: category }).then(res => {
        row._savedMsgCategory = category
        row.categorySource = 'MANUAL'
        row.categoryReason = '人工修改为' + category
        this.$modal.msgSuccess((res.data && res.data.message) || '分类已修改')
        this.getList()
      }).catch(() => {
        row.msgCategory = old
        this.$nextTick(() => this.$forceUpdate())
      })
    },
    handleAutoClassifyChange(enabled) {
      const target = !!enabled
      this.autoClassifySaving = true
      setWechatClassifyConfig(target).then(res => {
        const data = res.data || {}
        this.autoClassifyEnabled = typeof data.autoEnabled === 'boolean' ? data.autoEnabled : target
        this.$modal.msgSuccess(data.message || (target ? '自动分类已开启' : '自动分类已关闭'))
        this.loadStats()
      }).catch(() => {
        this.autoClassifyEnabled = !target
      }).finally(() => { this.autoClassifySaving = false })
    },
    handleAutoEntryChange(enabled) {
      const target = !!enabled
      this.autoEntrySaving = true
      setWechatEntryConfig(target).then(res => {
        const data = res.data || {}
        this.autoEntryEnabled = typeof data.autoEnabled === 'boolean' ? data.autoEnabled : target
        this.$modal.msgSuccess(data.message || (target ? '自动录入已开启' : '自动录入已关闭'))
        this.loadStats()
      }).catch(() => {
        this.autoEntryEnabled = !target
      }).finally(() => { this.autoEntrySaving = false })
    },
    handleEntrySelected() {
      if (!this.requireSelection()) return
      this.entering = true
      runWechatEntry({ keys: this.selectedKeys() }).then(res => {
        this.$modal.msgSuccess((res.data && res.data.message) || '录入完成')
        this.getList()
      }).finally(() => { this.entering = false })
    },
    handleRequeue() {
      if (!this.requireSelection()) return
      this.$modal.confirm('确认将选中的失败/跳过消息重新入队？').then(() => requeueWechatEntry({ keys: this.selectedKeys() })).then(res => {
        this.$modal.msgSuccess((res.data && res.data.message) || '已重排')
        this.getList()
      }).catch(() => {})
    },
    handleTraceScan() {
      scanWechatTrace().then(res => this.$modal.msgSuccess((res.data && res.data.message) || '补扫完成'))
    },
    handleClearCache() {
      this.$modal.confirm('确认清空分类缓存？历史分类结果不会被改写。').then(() => clearWechatClassifyCache()).then(res => {
        this.$modal.msgSuccess((res.data && res.data.message) || '已清空')
      }).catch(() => {})
    },
    quoteKey(row) {
      return row ? row.username + ':' + row.localId : ''
    },
    quoteInfoFor(row) {
      return this.quoteInfoMap[this.quoteKey(row)] || null
    },
    isQuoteImage(row) {
      const kind = this.quoteDisplayKind(row)
      return kind === '图片' || kind === 'image' || kind === '表情' || kind === 'emoji'
    },
    isQuoteFile(row) {
      const kind = this.quoteDisplayKind(row)
      return kind === '文件' || kind === 'file'
    },
    isQuotePlaceholderText(value) {
      return /^【(图片|表情|语音|视频|卡片|文件)】$/.test(String(value || '').trim())
    },
    quoteDisplayKind(row) {
      const info = this.quoteInfoFor(row)
      return (info && info.kind) || (row && row.quoteKind) || '内容'
    },
    quoteDisplayText(row) {
      if (this.isQuoteImage(row) || this.isQuoteFile(row)) {
        return ''
      }
      const info = this.quoteInfoFor(row)
      if (info && info.quote && !this.isQuotePlaceholderText(info.quote)) {
        return info.quote
      }
      return this.quoteBody(row)
    },
    quoteFileName(row) {
      const info = this.quoteInfoFor(row)
      if (info && info.fileName) {
        return info.fileName
      }
      return this.isQuoteFile(row) ? this.quoteBody(row) : ''
    },
    isQuotePlaceholder(row) {
      if (!row || !row.quote) return false
      if (this.isQuoteImage(row) || this.isQuoteFile(row)) return false
      return !this.quoteDisplayText(row) &&
        (this.isQuoteImage(row) || this.isQuoteFile(row) ||
         ['卡片', 'card', '语音', 'voice', '视频', 'video'].indexOf(String(this.quoteDisplayKind(row))) >= 0)
    },
    quoteNeedsInfo(row) {
      if (!row || !row.quote || this.quoteInfoFor(row)) return false
      const kind = String(row.quoteKind || '')
      if (['图片', 'image', '表情', 'emoji', '文件', 'file', '卡片', 'card'].indexOf(kind) >= 0) {
        return this.isQuotePlaceholder(row) || kind === '图片' || kind === 'image' ||
          kind === '表情' || kind === 'emoji' || kind === '文件' || kind === 'file'
      }
      return !this.quoteBody(row)
    },
    quoteInfoLoading(row) {
      return !!this.quoteInfoLoadingMap[this.quoteKey(row)]
    },
    quoteInfoError(row) {
      return this.quoteInfoErrorMap[this.quoteKey(row)] || ''
    },
    quoteMediaUrl(row) {
      return this.quoteMediaMap[this.quoteKey(row)] || ''
    },
    quoteMediaError(row) {
      return this.quoteMediaErrorMap[this.quoteKey(row)] || ''
    },
    quoteSummary(row) {
      if (this.isQuoteImage(row)) {
        return this.quoteMediaUrl(row) ? '图片（悬浮查看）' : (this.quoteMediaError(row) || '图片加载中')
      }
      if (this.isQuoteFile(row)) {
        return this.quoteFileName(row) || this.quoteInfoError(row) || '文件加载中'
      }
      return this.quoteDisplayText(row) || (this.quoteInfoLoading(row) ? '读取中…' : (this.quoteInfoError(row) || '无文字内容'))
    },
    loadQuoteInfo(row) {
      if (!row || !this.quoteNeedsInfo(row)) {
        return Promise.resolve(row)
      }
      const key = this.quoteKey(row)
      this.$set(this.quoteInfoLoadingMap, key, true)
      this.$set(this.quoteInfoErrorMap, key, '')
      return getWechatQuoteInfo({ username: row.username, localId: row.localId })
        .then(res => {
          if (res.data) {
            this.$set(this.quoteInfoMap, key, res.data)
          }
        })
        .catch(async error => {
          this.$set(this.quoteInfoErrorMap, key, await this.errorMessage(error, '引用详情读取失败'))
        })
        .finally(() => {
          this.$set(this.quoteInfoLoadingMap, key, false)
        })
    },
    loadQuoteDetails(rows) {
      const pending = (rows || [])
        .filter(row => this.quoteNeedsInfo(row))
        .map(row => this.loadQuoteInfo(row))
      return Promise.all(pending).then(() => {
        ;(rows || []).filter(row => this.isQuoteImage(row)).forEach(row => this.loadQuotePreview(row))
      })
    },
    async getQuoteMediaBlob(row, download, fallback) {
      let blob
      try {
        blob = await getWechatQuoteMedia({
          username: row.username,
          localId: row.localId,
          download: !!download
        })
      } catch (error) {
        throw new Error(await this.errorMessage(error, fallback))
      }
      if (!(blob instanceof Blob) || blob.size === 0) {
        throw new Error(fallback)
      }
      if (this.isJsonBlob(blob)) {
        throw new Error(await this.blobMessage(blob, fallback))
      }
      return blob
    },
    loadQuotePreview(row) {
      const key = this.quoteKey(row)
      if (!this.isQuoteImage(row) || this.quoteMediaMap[key]) {
        return Promise.resolve()
      }
      this.$set(this.quoteMediaErrorMap, key, '')
      return this.getQuoteMediaBlob(row, false, '引用图片读取失败')
        .then(blob => this.$set(this.quoteMediaMap, key, URL.createObjectURL(blob)))
        .catch(error => this.$set(this.quoteMediaErrorMap, key, error.message || '引用图片读取失败'))
    },
    releaseQuotePreviewMedia() {
      Object.keys(this.quoteMediaMap).forEach(key => URL.revokeObjectURL(this.quoteMediaMap[key]))
      this.quoteInfoMap = {}
      this.quoteMediaMap = {}
      this.quoteInfoLoadingMap = {}
      this.quoteInfoErrorMap = {}
      this.quoteMediaErrorMap = {}
    },
    showDetail(row) {
      this.releaseDetailMedia()
      this.releaseDetailQuoteMedia()
      this.detail = row
      this.detailMediaError = ''
      this.detailQuoteMediaError = ''
      this.detailOpen = true
      if (this.isImage(row)) {
        this.loadImagePreview(row)
      }
      this.loadQuoteDetails([row]).then(() => {
        if (this.isQuoteImage(row)) {
          this.loadDetailQuoteMedia(row)
        }
      })
    },
    closeDetail() {
      this.releaseDetailMedia()
      this.releaseDetailQuoteMedia()
      this.detail = {}
      this.detailMediaError = ''
      this.detailQuoteMediaError = ''
    },
    releaseDetailMedia() {
      if (this.detailMediaUrl) {
        URL.revokeObjectURL(this.detailMediaUrl)
        this.detailMediaUrl = ''
      }
    },
    releaseDetailQuoteMedia() {
      if (this.detailQuoteMediaUrl) {
        URL.revokeObjectURL(this.detailQuoteMediaUrl)
        this.detailQuoteMediaUrl = ''
      }
    },
    async loadDetailQuoteMedia(row) {
      this.releaseDetailQuoteMedia()
      this.detailQuoteMediaError = ''
      if (!this.isQuoteImage(row)) {
        return
      }
      this.detailQuoteMediaLoading = true
      try {
        const blob = await this.getQuoteMediaBlob(row, false, '引用图片读取失败')
        this.detailQuoteMediaUrl = URL.createObjectURL(blob)
      } catch (error) {
        this.detailQuoteMediaError = error.message || '引用图片读取失败'
      } finally {
        this.detailQuoteMediaLoading = false
      }
    },
    isImage(row) {
      return !!row && (row.type === '图片' || String(row.type || '').toUpperCase() === 'IMAGE')
    },
    hasFile(row) {
      if (!row) return false
      const type = String(row.type || '')
      const content = String(row.content || '')
      if (type === '文件' || type.toUpperCase() === 'FILE') {
        const ext = this.fileExtension(row)
        return !!ext && DOCUMENT_EXTS.indexOf(ext) >= 0
      }
      if (type !== '文件/链接/卡片' || !/<fileext(?:\s[^>]*)?>/i.test(content)) return false
      const ext = this.fileExtension(row)
      return !!ext && DOCUMENT_EXTS.indexOf(ext) >= 0
    },
    cardMeta(content) {
      const text = String(content || '')
      const readTag = tag => {
        const match = text.match(new RegExp('<' + tag + '(?:\\s[^>]*)?>([\\s\\S]*?)<\\/' + tag + '>', 'i'))
        if (!match) return ''
        const value = match[1].replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
        return this.decodeXml(value).trim()
      }
      return { title: readTag('title'), ext: readTag('fileext').replace(/^\./, '').toLowerCase() }
    },
    decodeXml(value) {
      const area = document.createElement('textarea')
      area.innerHTML = String(value || '')
      return area.value
    },
    cardTitle(content) {
      return this.cardMeta(content).title
    },
    fileExtension(row) {
      if (!row) return ''
      const meta = this.cardMeta(row.content)
      if (meta.ext) return meta.ext
      const name = meta.title || String(row.content || '')
      const dot = name.lastIndexOf('.')
      return dot >= 0 ? name.substring(dot + 1).replace(/[^a-zA-Z0-9]/g, '').toLowerCase() : ''
    },
    displayContent(row) {
      if (!row) return ''
      if (this.isImage(row)) return '【图片】'
      if (this.hasFile(row)) return this.cardTitle(row.content) || '微信文件'
      const content = String(row.content || '').trim()
      if (!content) return ''
      if (/^\s*(?:<\?xml[\s\S]*?\?>\s*)?<msg(?:\s|>)/i.test(content)) {
        return this.cardTitle(content) || '【卡片消息】'
      }
      return content
    },
    quoteBody(row) {
      if (!row) return ''
      const quote = String(row.quote || '').trim()
      if (!quote || this.isQuotePlaceholderText(quote)) {
        return ''
      }
      return quote
    },
    quoteKindIcon(kind) {
      const icons = {
        text: 'el-icon-document',
        图片: 'el-icon-picture-outline',
        image: 'el-icon-picture-outline',
        表情: 'el-icon-picture-outline',
        emoji: 'el-icon-picture-outline',
        语音: 'el-icon-microphone',
        voice: 'el-icon-microphone',
        视频: 'el-icon-video-camera',
        video: 'el-icon-video-camera',
        卡片: 'el-icon-chat-line-square',
        card: 'el-icon-chat-line-square',
        文件: 'el-icon-document',
        file: 'el-icon-document'
      }
      return icons[kind] || 'el-icon-chat-dot-square'
    },
    quoteKindLabel(kind) {
      const labels = {
        text: '文本',
        图片: '图片',
        image: '图片',
        表情: '表情',
        emoji: '表情',
        语音: '语音',
        voice: '语音',
        视频: '视频',
        video: '视频',
        卡片: '卡片',
        card: '卡片',
        文件: '文件',
        file: '文件',
        other: '其他'
      }
      return labels[kind] || kind || '内容'
    },
    isJsonBlob(blob) {
      return !!blob && String(blob.type || '').toLowerCase().indexOf('json') >= 0
    },
    async blobMessage(blob, fallback) {
      try {
        const data = JSON.parse(await blob.text())
        return data.msg || data.message || fallback
      } catch (e) {
        return fallback
      }
    },
    async errorMessage(error, fallback) {
      const data = error && error.response ? error.response.data : null
      if (data instanceof Blob) {
        return this.blobMessage(data, fallback)
      }
      if (data && (data.msg || data.message)) {
        return data.msg || data.message
      }
      const message = error && error.message ? error.message : ''
      if (message && message.indexOf('Request failed with status code') < 0) {
        return message
      }
      return fallback
    },
    async getMediaBlob(row, download, fallback, quote = false) {
      let blob
      try {
        const params = { username: row.username, localId: row.localId, download: !!download }
        blob = quote ? await getWechatQuoteMedia(params) : await getWechatMedia(params)
      } catch (error) {
        throw new Error(await this.errorMessage(error, fallback))
      }
      if (!(blob instanceof Blob) || blob.size === 0) {
        throw new Error(fallback)
      }
      if (this.isJsonBlob(blob)) {
        throw new Error(await this.blobMessage(blob, fallback))
      }
      return blob
    },
    async loadImagePreview(row) {
      this.releaseDetailMedia()
      this.detailMediaError = ''
      if (!row || !row.username) {
        this.detailMediaError = '消息缺少会话信息，无法读取图片'
        return
      }
      this.detailMediaLoading = true
      try {
        const blob = await this.getMediaBlob(row, false, '图片读取失败')
        this.detailMediaUrl = URL.createObjectURL(blob)
      } catch (error) {
        this.detailMediaError = error.message || '图片读取失败'
      } finally {
        this.detailMediaLoading = false
      }
    },
    downloadName(row, blob, quote = false) {
      const mime = blob && blob.type ? String(blob.type).toLowerCase() : ''
      let name = ''
      if (quote && this.isQuoteImage(row)) {
        name = '微信引用图片_' + row.localId + '.' + (IMAGE_MIME_EXTS[mime] || 'jpg')
      } else if (quote && this.isQuoteFile(row)) {
        name = this.quoteFileName(row) || ('微信引用文件_' + row.localId)
      } else if (!quote) {
        name = this.hasFile(row) ? this.cardTitle(row.content) : ''
        if (!name && this.isImage(row)) {
          name = '微信图片_' + row.localId + '.' + (IMAGE_MIME_EXTS[mime] || 'jpg')
        }
        if (!name) {
          const ext = this.fileExtension(row)
          name = '微信文件_' + row.localId + (ext ? '.' + ext : '')
        }
      }
      name = name || ('微信文件_' + row.localId)
      return name.replace(/[\\/:*?"<>|]/g, '_')
    },
    async downloadMedia(row, quote = false) {
      if (!row || !row.username || this.mediaDownloading) return
      this.mediaDownloading = true
      try {
        const blob = await this.getMediaBlob(row, true, quote ? '引用文件下载失败' : '文件下载失败', quote)
        saveAs(blob, this.downloadName(row, blob, quote))
      } catch (error) {
        this.$modal.msgError(error.message || '文件下载失败')
      } finally {
        this.mediaDownloading = false
      }
    },
    downloadQuoteMedia(row) {
      return this.downloadMedia(row, true)
    },
    downloadFilePreview() {
      return this.downloadMedia(this.filePreviewRow, this.filePreviewQuote)
    },
    quoteFileExtension(row) {
      const name = this.quoteFileName(row)
      const dot = name.lastIndexOf('.')
      return dot >= 0 ? name.substring(dot + 1).replace(/[^a-zA-Z0-9]/g, '').toLowerCase() : ''
    },
    openQuoteFilePreview(row) {
      return this.openFilePreview(row, true)
    },
    async openFilePreview(row, quote = false) {
      this.clearFilePreview()
      this.filePreviewRow = row
      this.filePreviewQuote = !!quote
      this.filePreviewName = quote ? (this.quoteFileName(row) || '引用文件') : (this.cardTitle(row.content) || '微信文件')
      this.filePreviewOpen = true
      this.filePreviewLoading = true
      try {
        const blob = await this.getMediaBlob(row, false, quote ? '引用文件读取失败' : '文件读取失败', quote)
        const ext = quote ? this.quoteFileExtension(row) : this.fileExtension(row)
        if (ext === 'pdf') {
          if (blob.size > 30 * 1024 * 1024) {
            throw new Error('PDF 超过 30MB，请下载后查看')
          }
          this.filePreviewKind = 'pdf'
          this.filePreviewUrl = URL.createObjectURL(blob)
          return
        }
        if (ext === 'xls' || ext === 'xlsx' || ext === 'csv') {
          if (blob.size > 10 * 1024 * 1024) {
            throw new Error('表格超过 10MB，请下载后查看')
          }
          let workbook
          if (ext === 'csv') {
            workbook = XLSX.read(await blob.text(), { type: 'string' })
          } else {
            workbook = XLSX.read(new Uint8Array(await blob.arrayBuffer()), { type: 'array' })
          }
          const sheetName = workbook.SheetNames[0]
          if (!sheetName) {
            throw new Error('文件中没有可预览的工作表')
          }
          const sheet = workbook.Sheets[sheetName]
          const range = XLSX.utils.decode_range(sheet['!ref'] || 'A1')
          const totalRows = range.e.r - range.s.r + 1
          const totalCols = range.e.c - range.s.c + 1
          const lastRow = Math.min(range.e.r, range.s.r + 199)
          const lastCol = Math.min(range.e.c, range.s.c + 49)
          const rows = []
          for (let rowIndex = range.s.r; rowIndex <= lastRow; rowIndex++) {
            const row = []
            for (let colIndex = range.s.c; colIndex <= lastCol; colIndex++) {
              const cell = sheet[XLSX.utils.encode_cell({ r: rowIndex, c: colIndex })]
              row.push(cell ? XLSX.utils.format_cell(cell) : '')
            }
            rows.push(row)
          }
          this.filePreviewKind = 'excel'
          this.filePreviewRows = rows
          this.filePreviewNote = '工作表：' + sheetName +
            (totalRows > 200 || totalCols > 50 ? '（仅预览前 200 行、50 列，完整内容请下载）' : '')
          return
        }
        if (TEXT_EXTS.indexOf(ext) >= 0) {
          if (blob.size > 2 * 1024 * 1024) {
            throw new Error('文本超过 2MB，请下载后查看')
          }
          this.filePreviewKind = 'text'
          this.filePreviewText = await blob.text()
          return
        }
        throw new Error('该文件类型暂不支持在线预览，请下载原文件')
      } catch (error) {
        this.filePreviewError = error.message || '文件读取失败'
      } finally {
        this.filePreviewLoading = false
      }
    },
    clearFilePreview() {
      if (this.filePreviewUrl) {
        URL.revokeObjectURL(this.filePreviewUrl)
      }
      this.filePreviewOpen = false
      this.filePreviewRow = {}
      this.filePreviewQuote = false
      this.filePreviewName = ''
      this.filePreviewError = ''
      this.filePreviewKind = ''
      this.filePreviewUrl = ''
      this.filePreviewText = ''
      this.filePreviewRows = []
      this.filePreviewNote = ''
      this.filePreviewLoading = false
    },
    pretty(value) {
      try { return JSON.stringify(JSON.parse(value), null, 2) } catch (e) { return value || '' }
    },
    categoryType(category) {
      if (category === 'INQ') return 'warning'
      if (category === 'OFFER') return 'success'
      if (category === 'TRACE') return 'danger'
      return 'info'
    },
    statusType(status) {
      if (status === 'SUCCESS' || status === 'SUCCESS_WARN' || status === 'GENERATED') return 'success'
      if (status === 'FAILED' || status === 'DEAD' || status === 'MANUAL_REVIEW') return 'danger'
      if (status === 'PROCESSING') return 'warning'
      return 'info'
    },
    taskStateReason(row) {
      if (!row) return ''
      const category = String(row.msgCategory || '').toUpperCase()
      if (category !== 'INQ' && category !== 'OFFER') return ''
      const task = String(row.taskStatus || '').toUpperCase()
      const entry = String(row.entryStatus || '').toUpperCase()
      if (row.taskFailureReason) return row.taskFailureReason
      if (task === 'PENDING') {
        if (entry === 'PENDING') return '待 AI 录入：消息已分类为 ' + category + '，但 entry_status=PENDING，任务生成不会扫描。配置位置：wechat_analysis_config.offerEntryEnabled。'
        if (entry === 'PROCESSING') return 'AI 录入处理中：entry_status=PROCESSING，录入完成后才会进入任务生成。'
        if (entry === 'FAILED' || entry === 'DEAD') return 'AI 录入失败：' + (row.entryMessage || 'entry_status=' + entry + '，请在微信消息列表重新入队。')
        if (entry === 'SKIPPED') return 'AI 录入已跳过：' + (row.entryMessage || '不满足自动录入条件，请检查群映射与录入配置。')
        if (entry === 'SUCCESS' || entry === 'SUCCESS_WARN') {
          return this.autoTaskGenerateEnabled
            ? '已录入成功，等待任务生成扫描（task_status=PENDING，自动生成已开启）。'
            : '已录入成功，但自动任务生成关闭（autoTaskGenerateEnabled=false），当前不会自动生成任务；请到“微信询报价任务”页面点击“生成任务”，或在 wechat_analysis_config 中开启自动任务生成。'
        }
        return row.taskMessage || '当前 task_status=PENDING，尚未进入任务生成；请检查 AI 录入状态。'
      }
      if (task === 'PROCESSING') return row.taskMessage || '任务生成处理中，领取超过 processingTimeoutSec 后可被重新扫描。'
      if (task === 'FAILED' || task === 'MANUAL_REVIEW' || task === 'SKIPPED') {
        return row.taskMessage || '任务未生成，请检查任务生成条件。'
      }
      return ''
    },
    taskStateReasonDanger(row) {
      const task = String((row && row.taskStatus) || '').toUpperCase()
      return task === 'FAILED' || task === 'MANUAL_REVIEW'
    }
  }
}
</script>

<style scoped>
.button-help-icon { margin-left: 4px; cursor: help; font-size: 13px; }
.category-select { width: 100px; }
.auto-classify-switch { line-height: 28px; white-space: nowrap; }
.auto-classify-label { margin-right: 6px; color: #606266; font-size: 13px; }
.message-hover { cursor: default; }
.message-content { line-height: 1.5; word-break: break-word; white-space: pre-wrap; }
.quote-preview { margin-top: 3px; color: #909399; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.quote-preview-label { color: #606266; }
.message-tooltip-content { max-width: 560px; line-height: 1.6; }
.message-tooltip-label { margin-bottom: 2px; color: #c0c4cc; font-size: 12px; }
.message-tooltip-text { white-space: pre-wrap; word-break: break-word; }
.message-tooltip-quote { margin-top: 8px; padding-top: 8px; border-top: 1px solid #606266; }
.message-tooltip-media { margin-bottom: 6px; }
.message-tooltip-muted { color: #c0c4cc; font-size: 12px; }
.message-tooltip-file { display: flex; align-items: center; gap: 6px; margin-top: 4px; }
.quote-hover-image { display: block; max-width: 420px; max-height: 240px; object-fit: contain; border-radius: 3px; background: #303133; }
.detail-text { white-space: pre-wrap; word-break: break-all; line-height: 1.6; }
.quote-box { border-left: 3px solid #dcdfe6; padding: 6px 10px; background: #f5f7fa; }
.quote-kind { display: inline-block; margin-bottom: 4px; color: #909399; font-size: 12px; }
.quote-empty { color: #909399; font-size: 13px; }
.quote-image-panel { min-height: 80px; margin-bottom: 6px; }
.quote-detail-image { display: block; max-width: 100%; max-height: 360px; }
.quote-file-card { margin: 6px 0; }
.media-panel { min-height: 80px; }
.image-preview { display: block; width: 100%; max-width: 860px; max-height: 520px; }
.media-actions { margin-top: 10px; }
.media-error { color: #f56c6c; line-height: 1.6; }
.file-card { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid #ebeef5; border-radius: 4px; background: #fafafa; }
.file-icon { color: #409eff; font-size: 22px; }
.file-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-preview { min-height: 180px; }
.pdf-preview { display: block; width: 100%; height: 72vh; border: 0; }
.text-preview { margin: 0; padding: 12px; max-height: 70vh; overflow: auto; white-space: pre-wrap; word-break: break-all; background: #f5f7fa; }
.excel-preview { max-height: 70vh; overflow: auto; }
.sheet-note { margin-bottom: 8px; color: #909399; font-size: 12px; }
.excel-table { border-collapse: collapse; font-size: 12px; }
.excel-table td, .excel-table th { border: 1px solid #dcdfe6; padding: 4px 8px; white-space: nowrap; }
.excel-table th { position: sticky; left: 0; background: #f5f7fa; color: #909399; font-weight: normal; }
.json-box { white-space: pre-wrap; word-break: break-all; background: #f5f7fa; padding: 8px; margin: 0; max-height: 240px; overflow: auto; }
.text-danger { color: #f56c6c; }
</style>

<style>
.wechat-help-popover { line-height: 1.6; }
.wechat-help { max-height: 70vh; overflow-y: auto; padding-right: 6px; font-size: 12px; color: #303133; }
.wechat-help .help-title { margin: 10px 0 4px; color: #409eff; font-weight: 600; }
.wechat-help .help-title:first-child { margin-top: 0; }
.wechat-help ul { margin: 0; padding-left: 18px; }
.wechat-help li { margin: 3px 0; }
.wechat-help code { padding: 1px 4px; border-radius: 2px; background: #f5f7fa; color: #c7254e; }
</style>
