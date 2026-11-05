<template>
  <div class="app-container">
    <el-form ref="queryForm" :model="queryParams" size="small" :inline="true" v-show="showSearch">
      <el-form-item label="料号" prop="pnKeyword">
        <el-input v-model="queryParams.pnKeyword" placeholder="料号" clearable style="width:150px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="聊天对象" prop="oriTarget">
        <el-input v-model="queryParams.oriTarget" placeholder="群名或好友名" clearable style="width:160px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="内容" prop="queryKeyword">
        <el-input v-model="queryParams.queryKeyword" placeholder="原文或发送内容" clearable style="width:170px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="任务类型" prop="taskType">
        <el-select v-model="queryParams.taskType" clearable placeholder="全部" style="width:130px">
          <el-option v-for="item in taskTypes" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="handleStatus">
        <el-select v-model="queryParams.handleStatus" clearable placeholder="全部" style="width:165px">
          <el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="仅异常">
        <el-switch v-model="queryParams.exceptionOnly" />
      </el-form-item>
      <el-form-item label="创建时间">
        <el-date-picker v-model="dateRange" type="daterange" value-format="yyyy-MM-dd" range-separator="-" start-placeholder="开始日期" end-placeholder="结束日期" style="width:250px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
        <el-popover placement="bottom-start" width="720" trigger="hover" popper-class="wechat-help-popover">
          <div class="wechat-help">
            <div class="help-title">菜单按钮说明</div>
            <ul>
              <li><b>生成任务</b>：立即扫描消息并生成任务；任务先按“待审批”写入，若发送目标对应的供应商均开启 <code>crm_supplier.auto_approve</code>，再调用批量审批流程转为“已审批待发送”。点击后弹窗会显示本次扫描对象、起止时间、单批上限、全部必要条件、阶段统计和实际扫描数量。人工点击不受 <code>autoTaskGenerateEnabled=false</code> 限制。</li>
              <li><b>任务生成开关</b>：开启后由“微信自动化-INQ/OFFER任务生成”定时任务扫描并生成任务；关闭后定时任务不再生成，但“生成任务”按钮仍可人工执行。</li>
              <li><b>生成时间范围</b>：按消息 <code>create_time</code> 计算，从当前时刻向前滚动 <code>msgRetentionDays</code> 天（默认 30 天），不是只扫描今天；弹窗中的“开始时间/结束时间”就是本次实际 SQL 条件。</li>
              <li><b>生成必要条件</b>：分类成功且来源为 LLM/CACHE/MANUAL、分类为 INQ/OFFER；AI 录入必须为 <code>SUCCESS/SUCCESS_WARN</code>，任务状态为 <code>PENDING/FAILED</code>，或任务处理超时。群未映射/群映射冲突只会转人工复核，不再创建空料号异常任务。单次最多领取 <code>taskGenerateBatchSize=20</code> 条。</li>
              <li><b>为什么 task_status=PENDING 仍扫描 0 条</b>：<code>task_status=PENDING</code> 只表示“尚未生成任务”，还需 <code>entry_status</code> 已录入成功。若录入仍为 <code>PENDING</code>，消息会停留在等待 AI 录入阶段，不会进入任务生成；生成结果弹窗会单独显示该数量。</li>
              <li><b>任务状态感叹号</b>：微信消息列表的任务状态旁会显示当前卡点；<code>PENDING</code> 会区分“待 AI 录入 / AI 录入处理中 / 已录入等待任务生成扫描”，失败、转人工或异常跳过则显示具体原因。数据库字段 <code>task_failure_reason</code> 只记录失败类原因，等待扫描的说明由页面按状态实时计算。</li>
              <li><b>发送扫描</b>：立即扫描并发送“已审批待发送”任务；人工点击不受 <code>autoSendEnabled=false</code> 限制，但当前微信登录账号必须是 <code>autoSendAccount</code>（默认 Eke），否则停止发送。网关返回失败时一律按“结果未知”处理，不再自动重试，避免消息实际已发出后重复发送。</li>
              <li><b>自动发送开关</b>：开启后由定时任务持续扫描“已审批待发送”任务并发送；关闭后定时发送停止，“发送扫描”按钮仍可人工执行。</li>
              <li><b>审批/编辑</b>：查看来源消息、OFFER 快照、关联 INQ、发送目标和发送预览，并修改任务状态、对外报价、发送内容和发送目标。</li>
              <li><b>批量审批</b>：勾选列表多行后点击，只审批当前为 <code>PENDING_APPROVAL</code> 的任务；其他状态会跳过，失败数量在结果提示中返回。</li>
              <li><b>确认未发送</b>：仅当最近异常以 <code>GATEWAY_RESULT_UNKNOWN</code> 开头时出现；人工确认网关确实未发送后，任务恢复为“已审批待发送”，重试次数清零。</li>
              <li><b>删除/批量删除</b>：删除任务时弹窗选择“是否回退数据”。选“否”只删除任务；选“是”会把来源微信消息的 <code>task_status</code> 重置为 <code>PENDING</code>，便于重新生成，但不会改变分类、AI 录入和已生成的 OFFER。</li>
              <li><b>纯缺货发送</b>：同一发送组全部为缺货/无库存回复时，只发送缺货原文，不添加 <code>M&amp;E 日期 Offer：</code>、<code>Part No. Qty Price</code> 表头和品牌行；同组含正常报价时仍按正常格式合并。</li>
              <li><b>仅异常</b>：显示发送目标为空的任务。历史 <code>UNMAPPED_CHAT</code> 空料号任务已清理，新数据不会再生成。</li>
            </ul>
            <div class="help-title">任务状态</div>
            <ul>
              <li><b>待审批</b>：任务已生成，不参与发送。</li>
              <li><b>已审批待发送</b>：唯一允许发送扫描的状态；修改后未选择状态时保持原状态。</li>
              <li><b>已审批已发送</b>：所有目标成功，或人工认定结束；系统不再自动发送。</li>
              <li><b>无需处理</b>：驳回或业务确认不处理；仍可再次进入审批。</li>
              <li>部分成功不新增独立状态，用“发送目标 x / 成功 y”、最近异常和发送次数表示；失败目标按“结果未知”处理，核实未发送后可点“确认未发送”再人工重试。</li>
            </ul>
            <div class="help-title">INQ 分流规则</div>
            <ul>
              <li>外部客户发来 INQ 时，系统先读取聊天对象对应供应商档案的“合作等级”（<code>crm_supplier.cooperation_level</code>）。</li>
              <li>合作等级包含 <b>S / A / B</b>：按产品方向查找其他启用供应商，向有销售群的供应商生成“供应商询价”任务。</li>
              <li>合作等级为 <b>C / D / E 或为空</b>：不向其他供应商询价，直接按料号查询系统内有效 Offer；有记录生成“客户回复”任务并带入系统报价，无记录生成内容为 <code>No stock</code> 的“客户回复”任务。</li>
              <li>系统 Offer 只取有效状态、Offer 类型、报价大于 0 且币种为 USD（或空）的记录，料号按双向前缀匹配，优先最低价、同价取最新；有效期默认最近 2 个自然日（昨天 00:00 至当前），由字典 <code>wechat_analysis_config</code> 的 <code>inqMatchDayNo=2</code> 控制。</li>
              <li>低等级客户的回复目标固定为当前客户会话；任务默认“待审批”，仅当所有发送目标对应的供应商都开启“自动审批”时，才会通过批量审批流程转为“已审批待发送”。</li>
            </ul>
            <div class="help-title">审批与编辑</div>
            <ul>
              <li>打开“待审批”或“无需处理”任务时，状态默认“已审批待发送”；打开“已审批待发送/已审批已发送”任务时默认保持原状态。</li>
              <li>可修改对外报价：改价且未手工修改发送内容时，系统按新价格重新生成该料号行片段。</li>
              <li>可修改发送内容和发送目标；“已审批待发送”必须至少有一个发送目标。</li>
              <li>保存后记录审批人、审批时间和非必填审批原因；最终文本按目标合并所有已审批任务，审批修改后的内容优先。</li>
            </ul>
            <div class="help-title">发送扫描细节</div>
            <ul>
              <li>只处理 <code>APPROVED_PENDING_SEND</code>、发送目标非空、发送次数小于 <code>taskSendMaxAttempts</code>（默认 3）且非结果未知的任务。</li>
              <li>单次最多扫描 <code>taskSendScanBatchSize=200</code> 条，按 <code>taskSendBatchSize=20</code> 组分批发送；同一任务类型+同一群名合并为一条消息。</li>
              <li>每个目标成功即写入成功清单；全部成功置“已审批已发送”；失败目标保留在发送目标中，但统一标记 <code>GATEWAY_RESULT_UNKNOWN</code>，不会自动进入下一轮。</li>
              <li>网关返回失败、异常或结果不完整时停止本轮并标记为结果未知，需人工核实确实未发送后点“确认未发送”，避免重复发送。</li>
              <li>自动扫描由 <code>autoSendEnabled=true</code> 开启，间隔 <code>taskSendScanSec=30</code> 秒。</li>
            </ul>
            <div class="help-title">配置位置</div>
            <ul>
              <li>系统管理 → 字典管理 → 字典类型 <code>wechat_analysis_config</code>。</li>
              <li><b>生成</b>：<code>autoTaskGenerateEnabled</code>、<code>taskGenerateScanSec</code>、<code>taskGenerateBatchSize</code>、<code>msgRetentionDays</code>。</li>
              <li><b>发送</b>：<code>autoSendEnabled</code>、<code>taskSendScanSec</code>、<code>taskSendMaxAttempts</code>、<code>taskSendScanBatchSize</code>、<code>taskSendBatchSize</code>、<code>autoSendAccount</code>。</li>
              <li><b>INQ 直接回复</b>：<code>inqMatchDayNo</code>，默认 2，表示昨天 00:00 至当前；该窗口同时用于客户 INQ 与供应商 OFFER 的关联。</li>
              <li><b>待跟进告警</b>：<code>offerFollowUpMaxAgeMin</code>，默认 360 分钟；超过该时效的历史 OFFER 补录后仍入库、生成任务，但不再推送“Offer待跟进”飞书消息。</li>
            </ul>
          </div>
          <el-button slot="reference" icon="el-icon-question" size="mini">说明</el-button>
        </el-popover>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="3" class="task-switch">
        <span class="task-switch-label">任务生成</span>
        <el-switch v-model="autoTaskGenerateEnabled" :disabled="taskSwitchesLoading || taskGenerateSaving" active-color="#13ce66" v-hasPermi="['wechat:task:generate']" @change="handleTaskGenerateChange" />
      </el-col>
      <el-col :span="3" class="task-switch">
        <span class="task-switch-label">自动发送</span>
        <el-switch v-model="autoSendEnabled" :disabled="taskSwitchesLoading || taskSendSaving" active-color="#13ce66" v-hasPermi="['wechat:task:send']" @change="handleTaskSendChange" />
      </el-col>
      <el-col :span="1.5"><el-button type="primary" plain icon="el-icon-magic-stick" size="mini" :loading="generating" @click="handleGenerate" v-hasPermi="['wechat:task:generate']">生成任务</el-button></el-col>
      <el-col :span="1.5"><el-button type="success" plain icon="el-icon-s-promotion" size="mini" :loading="sending" @click="handleSend" v-hasPermi="['wechat:task:send']">发送扫描</el-button></el-col>
      <el-col :span="1.5"><el-button type="warning" plain icon="el-icon-check" size="mini" :disabled="selectedRows.length === 0" @click="handleBatchApprove" v-hasPermi="['wechat:task:approve']">批量审批</el-button></el-col>
      <el-col :span="1.5"><el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="selectedRows.length === 0" @click="openDeleteDialog(null)" v-hasPermi="['wechat:task:remove']">批量删除</el-button></el-col>
      <span v-if="selectedRows.length" class="selection-tip">已选择 {{ selectedRows.length }} 条</span>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="taskList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="46" align="center" />
      <el-table-column label="任务 ID" prop="id" width="80" align="center" />
      <el-table-column label="创建时间" prop="createTime" width="158" />
      <el-table-column label="INQ/OFFER 发生时间" prop="sourceMessageTime" width="165" />
      <el-table-column label="来源对象" prop="oriTarget" min-width="160" show-overflow-tooltip />
      <el-table-column label="来源分类" prop="sourceCategory" width="90" align="center" />
      <el-table-column label="任务类型" width="125" align="center">
        <template slot-scope="scope"><el-tag size="mini" :type="taskTypeTag(scope.row.taskType)">{{ taskTypeLabel(scope.row.taskType) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="料号" prop="pnOriginal" width="170" show-overflow-tooltip />
      <el-table-column label="数量" prop="qty" width="75" align="right" />
      <el-table-column label="成本价" prop="oriPrice" width="90" align="right" />
      <el-table-column label="对外报价" prop="offerPrice" width="100" align="right" />
      <el-table-column label="发送目标" width="105" align="center">
        <template slot-scope="scope">{{ (scope.row.targets || []).length }} / 成功 {{ (scope.row.successTargets || []).length }}</template>
      </el-table-column>
      <el-table-column label="发送次数" prop="sendAttempts" width="85" align="center" />
      <el-table-column label="状态" width="165" align="center">
        <template slot-scope="scope"><el-tag size="mini" :type="statusTag(scope.row.handleStatus)">{{ statusLabel(scope.row.handleStatus) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="异常" prop="lastError" min-width="150" show-overflow-tooltip>
        <template slot-scope="scope"><span class="text-danger">{{ scope.row.lastError || '-' }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="235" fixed="right" align="center">
        <template slot-scope="scope">
          <el-button type="text" size="mini" icon="el-icon-edit-outline" @click="openTask(scope.row.id)" v-hasPermi="['wechat:task:query']">审批</el-button>
          <el-button v-if="isUnknownResult(scope.row)" type="text" size="mini" icon="el-icon-warning-outline" @click="confirmNotSent(scope.row)" v-hasPermi="['wechat:task:send']">确认未发送</el-button>
          <el-button type="text" size="mini" icon="el-icon-delete" class="text-danger" @click="openDeleteDialog(scope.row)" v-hasPermi="['wechat:task:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />

    <el-dialog title="任务生成扫描结果" :visible.sync="generateResultOpen" width="860px" append-to-body>
      <el-alert v-if="generateResult.message" :title="generateResult.message" type="info" :closable="false" show-icon />
      <el-alert v-if="generateResult.statsAvailable === false" title="阶段诊断统计查询失败，请查看后端日志；本次扫描结果仍以实际扫描数量为准。" type="error" :closable="false" show-icon class="mt10" />
      <el-descriptions :column="2" border size="small" class="mt10">
        <el-descriptions-item label="扫描方式">{{ generateResult.mode === 'MANUAL' ? '人工点击' : '定时扫描' }}</el-descriptions-item>
        <el-descriptions-item label="扫描对象">{{ generateResult.chatScope || '-' }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ generateResult.since || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ generateResult.until || '-' }}</el-descriptions-item>
        <el-descriptions-item label="时间窗口">最近 {{ generateResult.retentionDays == null ? '-' : generateResult.retentionDays }} 天（滚动窗口）</el-descriptions-item>
        <el-descriptions-item label="单批上限">{{ generateResult.batchSize == null ? '-' : generateResult.batchSize }} 条</el-descriptions-item>
        <el-descriptions-item label="扫描前符合条件">{{ statValue('readyToGenerate') }} 条</el-descriptions-item>
        <el-descriptions-item label="本次实际扫描">{{ generateResult.scanned == null ? 0 : generateResult.scanned }} 条</el-descriptions-item>
        <el-descriptions-item label="生成消息">{{ generateResult.generatedMessages == null ? 0 : generateResult.generatedMessages }} 条</el-descriptions-item>
        <el-descriptions-item label="生成任务">{{ generateResult.generatedTasks == null ? 0 : generateResult.generatedTasks }} 条</el-descriptions-item>
        <el-descriptions-item label="跳过消息">{{ generateResult.skippedMessages == null ? 0 : generateResult.skippedMessages }} 条</el-descriptions-item>
        <el-descriptions-item label="失败消息">{{ generateResult.failedMessages == null ? 0 : generateResult.failedMessages }} 条</el-descriptions-item>
      </el-descriptions>
      <div class="dialog-section-title">阶段诊断</div>
      <el-table :data="generateDiagnostics" size="mini" border>
        <el-table-column label="阶段" prop="label" width="250" />
        <el-table-column label="消息数" prop="value" width="100" align="right" />
        <el-table-column label="说明" prop="hint" min-width="360" show-overflow-tooltip />
      </el-table>
      <div class="dialog-section-title">本次扫描条件</div>
      <ul class="condition-list">
        <li v-for="item in (generateResult.conditions || [])" :key="item">{{ item }}</li>
      </ul>
      <div slot="footer" class="dialog-footer"><el-button type="primary" @click="generateResultOpen = false">知道了</el-button></div>
    </el-dialog>

    <el-dialog title="删除询报价任务" :visible.sync="deleteDialogOpen" width="620px" append-to-body>
      <el-alert :title="'将删除 ' + deleteRows.length + ' 条任务；本操作不会撤回已经发送到微信的消息。'" type="warning" :closable="false" show-icon />
      <div class="delete-question">是否回退数据？</div>
      <el-radio-group v-model="deleteRevertSource">
        <el-radio label="0">否：仅删除任务，来源微信消息状态保持不变</el-radio>
        <el-radio label="1">是：删除后把来源微信消息的任务生成状态重置为 PENDING，便于重新生成</el-radio>
      </el-radio-group>
      <el-alert v-if="deleteRevertSource === '1'" title="回退只重置 task_status，不改分类、AI 录入状态和已生成 OFFER。若同一来源消息仍有其他任务，重新生成时会被现有任务去重。" type="info" :closable="false" show-icon class="mt10" />
      <div slot="footer" class="dialog-footer">
        <el-button @click="deleteDialogOpen = false">取消</el-button>
        <el-button type="danger" :loading="deleteSubmitting" @click="submitDelete">确认删除</el-button>
      </div>
    </el-dialog>

    <el-dialog :title="'任务 #' + form.id + ' 审批与编辑'" :visible.sync="open" width="980px" append-to-body>
      <el-descriptions v-if="form.id" :column="2" border size="small" class="mb8">
        <el-descriptions-item label="任务 ID">{{ form.id }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ form.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源对象">{{ form.oriTarget || '-' }}</el-descriptions-item>
        <el-descriptions-item label="INQ/OFFER 发生时间">{{ form.sourceMessageTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源分类">{{ form.sourceCategory || '-' }}</el-descriptions-item>
        <el-descriptions-item label="任务类型">{{ taskTypeLabel(form.taskType) }}</el-descriptions-item>
        <el-descriptions-item label="料号">{{ form.pnOriginal || '-' }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ form.qty == null ? '-' : form.qty }}</el-descriptions-item>
        <el-descriptions-item label="成本价">{{ form.oriPrice == null ? '-' : form.oriPrice }}</el-descriptions-item>
        <el-descriptions-item label="对外报价">{{ form.offerPrice == null ? '-' : form.offerPrice }}</el-descriptions-item>
        <el-descriptions-item label="发送状态">{{ statusLabel(form.handleStatus) }}</el-descriptions-item>
        <el-descriptions-item label="审批人">{{ form.approvedBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批时间">{{ form.approvedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发送次数">{{ form.sendAttempts == null ? 0 : form.sendAttempts }}</el-descriptions-item>
        <el-descriptions-item label="最近发送时间">{{ form.lastAttemptAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="最近异常" :span="2"><span class="text-danger">{{ form.lastError || '-' }}</span></el-descriptions-item>
        <el-descriptions-item label="审批原因" :span="2">{{ form.approvalReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源消息" :span="2"><div class="detail-text">{{ form.oriMsg || '-' }}</div></el-descriptions-item>
        <el-descriptions-item label="OFFER 快照" :span="2"><pre class="json-box">{{ pretty(form.offerJson) }}</pre></el-descriptions-item>
        <el-descriptions-item label="关联 INQ" :span="2"><pre class="json-box">{{ pretty(form.matchedInqJson) }}</pre></el-descriptions-item>
      </el-descriptions>

      <el-form ref="form" :model="form" label-width="105px" size="small">
        <el-form-item label="任务状态">
          <el-select v-model="form.handleStatus" style="width:240px">
            <el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="对外报价">
          <el-input-number v-model="form.offerPrice" :controls="false" :precision="2" :min="0" style="width:240px" />
        </el-form-item>
        <el-form-item label="发送内容">
          <el-input v-model="form.sendContent" type="textarea" :rows="6" />
        </el-form-item>
        <el-form-item label="审批原因">
          <el-input v-model="form.approvalReason" maxlength="500" placeholder="可选" />
        </el-form-item>
        <el-form-item label="发送目标">
          <el-button size="mini" icon="el-icon-plus" @click="addTarget">添加目标</el-button>
          <el-table :data="form.targets || []" size="mini" border class="target-table">
            <el-table-column label="群名" min-width="220"><template slot-scope="scope"><el-input v-model="scope.row.name" size="mini" /></template></el-table-column>
            <el-table-column label="伙伴编码" width="150"><template slot-scope="scope"><el-input v-model="scope.row.partyCode" size="mini" /></template></el-table-column>
            <el-table-column label="伙伴名称" min-width="160"><template slot-scope="scope"><el-input v-model="scope.row.partyName" size="mini" /></template></el-table-column>
            <el-table-column label="操作" width="70" align="center"><template slot-scope="scope"><el-button type="text" class="text-danger" @click="removeTarget(scope.$index)">移除</el-button></template></el-table-column>
          </el-table>
        </el-form-item>
        <el-form-item label="成功目标">
          <el-tag v-for="item in form.successTargets || []" :key="item.name" size="small" type="success" class="mr5">{{ item.name }}</el-tag>
          <span v-if="!(form.successTargets || []).length">-</span>
        </el-form-item>
        <el-form-item label="发送预览">
          <el-table :data="form.previews || []" size="mini" border class="target-table">
            <el-table-column label="目标" min-width="150">
              <template slot-scope="scope">
                <div>{{ scope.row.targetName }}</div>
                <div class="preview-party">{{ scope.row.partyName || '-' }}</div>
              </template>
            </el-table-column>
            <el-table-column label="最终合并文本" min-width="520">
              <template slot-scope="scope"><pre class="preview-box">{{ scope.row.text || '暂无已审批内容' }}</pre></template>
            </el-table-column>
          </el-table>
          <span v-if="!(form.previews || []).length" class="text-muted">暂无发送目标</span>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitApproval" v-hasPermi="['wechat:task:approve']">保存审批</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listWechatTasks, getWechatTask, getWechatTaskConfig, setWechatTaskGenerateConfig, setWechatTaskSendConfig, approveWechatTask, batchApproveWechatTasks, batchRemoveWechatTasks, generateWechatTasks, sendWechatTasks, confirmWechatTaskNotSent } from '@/api/crm/wechatTask'

export default {
  name: 'CrmWechatTask',
  data() {
    return {
      loading: false,
      saving: false,
      generating: false,
      sending: false,
      autoTaskGenerateEnabled: false,
      autoSendEnabled: false,
      taskSwitchesLoading: false,
      taskGenerateSaving: false,
      taskSendSaving: false,
      showSearch: true,
      total: 0,
      taskList: [],
      selectedRows: [],
      dateRange: [],
      open: false,
      form: {},
      generateResultOpen: false,
      generateResult: {},
      deleteDialogOpen: false,
      deleteSubmitting: false,
      deleteRevertSource: '0',
      deleteRows: [],
      statuses: [
        { value: 'PENDING_APPROVAL', label: '待审批' },
        { value: 'APPROVED_PENDING_SEND', label: '已审批待发送' },
        { value: 'SENT', label: '已审批已发送' },
        { value: 'NO_ACTION', label: '无需处理' }
      ],
      taskTypes: [
        { value: 'SUPPLIER_INQUIRY', label: '供应商询价' },
        { value: 'CUSTOMER_REPLY', label: '客户回复' },
        { value: 'CUSTOMER_PUSH', label: '客户推送' },
        { value: 'UNMAPPED_CHAT', label: '未映射异常' }
      ],
      queryParams: { pageNum: 1, pageSize: 20, pnKeyword: undefined, oriTarget: undefined, queryKeyword: undefined, taskType: undefined, handleStatus: undefined, exceptionOnly: false, beginTime: undefined, endTime: undefined }
    }
  },
  computed: {
    generateDiagnostics() {
      const stats = this.generateResult.stats || {}
      return [
        { key: 'classifiedTotal', label: '窗口内已分类 INQ/OFFER', hint: '通过分类、分类来源和消息时间条件' },
        { key: 'taskPending', label: '任务状态为 PENDING', hint: '仅代表尚未生成任务，不等于已满足生成条件' },
        { key: 'taskEntryPending', label: '其中：等待 AI 录入', hint: 'entry_status=PENDING，不会进入任务生成' },
        { key: 'taskEntryProcessing', label: '其中：AI 录入处理中', hint: 'entry_status=PROCESSING' },
        { key: 'taskEntryFailed', label: '其中：AI 录入失败/终止', hint: 'entry_status=FAILED/DEAD' },
        { key: 'taskEntrySkipped', label: '其中：AI 录入异常跳过', hint: 'entry_status=SKIPPED' },
        { key: 'entryReady', label: '其中：已录入且待生成', hint: 'entry_status=SUCCESS/SUCCESS_WARN 且 task_status=PENDING' },
        { key: 'readyToGenerate', label: '扫描前符合生成条件', hint: '含群映射异常和任务处理超时，尚未受单批上限影响' },
        { key: 'taskGenerated', label: '已生成任务', hint: 'task_status=GENERATED' },
        { key: 'taskManualReview', label: '转人工复核', hint: 'task_status=MANUAL_REVIEW' },
        { key: 'taskSkipped', label: '生成跳过', hint: 'task_status=SKIPPED' },
        { key: 'taskFailed', label: '生成失败', hint: 'task_status=FAILED' },
        { key: 'taskFailureRecorded', label: '已记录任务失败原因', hint: 'task_failure_reason 非空' }
      ].map(item => Object.assign(item, { value: stats[item.key] == null ? 0 : stats[item.key] }))
    }
  },
  created() {
    this.dateRange = this.last30Days()
    this.loadTaskSwitches()
    this.handleQuery()
  },
  methods: {
    last30Days() {
      const fmt = date => date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0')
      const now = new Date()
      return [fmt(new Date(now.getTime() - 29 * 86400000)), fmt(now)]
    },
    getList() {
      this.loading = true
      const query = Object.assign({}, this.queryParams)
      if (!query.exceptionOnly) delete query.exceptionOnly
      if (this.dateRange && this.dateRange.length === 2) {
        query.beginTime = this.dateRange[0] + ' 00:00:00'
        query.endTime = this.dateRange[1] + ' 23:59:59'
      }
      listWechatTasks(query).then(res => {
        this.taskList = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    loadTaskSwitches() {
      this.taskSwitchesLoading = true
      getWechatTaskConfig().then(res => {
        const data = res.data || {}
        this.autoTaskGenerateEnabled = !!data.autoTaskGenerateEnabled
        this.autoSendEnabled = !!data.autoSendEnabled
      }).finally(() => { this.taskSwitchesLoading = false })
    },
    handleTaskGenerateChange(enabled) {
      const target = !!enabled
      this.taskGenerateSaving = true
      setWechatTaskGenerateConfig(target).then(res => {
        const data = res.data || {}
        this.autoTaskGenerateEnabled = typeof data.autoTaskGenerateEnabled === 'boolean' ? data.autoTaskGenerateEnabled : target
        this.$modal.msgSuccess(data.message || (target ? '任务自动生成已开启' : '任务自动生成已关闭'))
      }).catch(() => {
        this.autoTaskGenerateEnabled = !target
      }).finally(() => { this.taskGenerateSaving = false })
    },
    handleTaskSendChange(enabled) {
      const target = !!enabled
      this.taskSendSaving = true
      setWechatTaskSendConfig(target).then(res => {
        const data = res.data || {}
        this.autoSendEnabled = typeof data.autoSendEnabled === 'boolean' ? data.autoSendEnabled : target
        this.$modal.msgSuccess(data.message || (target ? '任务自动发送已开启' : '任务自动发送已关闭'))
      }).catch(() => {
        this.autoSendEnabled = !target
      }).finally(() => { this.taskSendSaving = false })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.resetForm('queryForm')
      this.dateRange = this.last30Days()
      this.queryParams.exceptionOnly = false
      this.handleQuery()
    },
    handleSelectionChange(rows) {
      this.selectedRows = rows || []
    },
    handleGenerate() {
      this.generating = true
      generateWechatTasks({}).then(res => {
        this.generateResult = res.data || {}
        this.generateResultOpen = true
        this.getList()
      }).finally(() => { this.generating = false })
    },
    statValue(key) {
      const stats = this.generateResult.stats || {}
      return stats[key] == null ? 0 : stats[key]
    },
    handleSend() {
      this.$confirm('确认扫描并发送所有已审批任务？', '提示', { type: 'warning' }).then(() => {
        this.sending = true
        return sendWechatTasks().then(res => {
          this.$modal.msgSuccess((res.data && res.data.message) || '发送扫描完成')
          this.getList()
        }).finally(() => { this.sending = false })
      }).catch(() => {})
    },
    openTask(id) {
      getWechatTask(id).then(res => {
        this.form = Object.assign({}, res.data || {})
        this.form.targets = (this.form.targets || []).map(item => Object.assign({}, item))
        if (['PENDING_APPROVAL', 'NO_ACTION'].indexOf(String(this.form.handleStatus || '')) >= 0) {
          this.form.handleStatus = 'APPROVED_PENDING_SEND'
        }
        this.open = true
      })
    },
    addTarget() {
      if (!this.form.targets) this.$set(this.form, 'targets', [])
      this.form.targets.push({ name: '', partyCode: '', partyName: '' })
    },
    removeTarget(index) {
      this.form.targets.splice(index, 1)
    },
    submitApproval() {
      const targets = (this.form.targets || []).filter(item => String(item.name || '').trim())
      if (this.form.handleStatus === 'APPROVED_PENDING_SEND' && targets.length === 0) {
        this.$modal.msgError('已审批待发送任务必须至少有一个发送目标')
        return
      }
      this.saving = true
      approveWechatTask(this.form.id, Object.assign({}, this.form, { targets: targets })).then(() => {
        this.$modal.msgSuccess('审批已保存')
        this.open = false
        this.getList()
      }).finally(() => { this.saving = false })
    },
    confirmNotSent(row) {
      this.$confirm('确认网关确实未发送，恢复该任务为待发送？', '风险确认', { type: 'warning' }).then(() => {
        return confirmWechatTaskNotSent(row.id).then(() => {
          this.$modal.msgSuccess('已恢复为待发送')
          this.getList()
        })
      }).catch(() => {})
    },
    handleBatchApprove() {
      const pending = this.selectedRows.filter(item => item.handleStatus === 'PENDING_APPROVAL')
      if (pending.length === 0) {
        this.$modal.msgWarning('所选任务中没有“待审批”任务')
        return
      }
      const skipped = this.selectedRows.length - pending.length
      this.$confirm('已选择 ' + this.selectedRows.length + ' 条，其中待审批 ' + pending.length + ' 条' +
        (skipped > 0 ? '，其他 ' + skipped + ' 条将跳过' : '') + '。确认批量审批？', '批量审批', { type: 'warning' }).then(() => {
        return batchApproveWechatTasks({ ids: pending.map(item => item.id), approvalReason: '列表批量审批' }).then(res => {
          const data = res.data || {}
          if (data.failed > 0 && data.errors && data.errors.length) {
            this.$modal.msgWarning(data.message + '；' + data.errors[0])
          } else {
            this.$modal.msgSuccess(data.message || '批量审批完成')
          }
          this.getList()
        })
      }).catch(() => {})
    },
    openDeleteDialog(row) {
      this.deleteRows = row ? [row] : (this.selectedRows || []).slice()
      if (this.deleteRows.length === 0) {
        this.$modal.msgWarning('请先选择要删除的任务')
        return
      }
      this.deleteRevertSource = '0'
      this.deleteDialogOpen = true
    },
    submitDelete() {
      if (this.deleteRows.length === 0) return
      this.deleteSubmitting = true
      batchRemoveWechatTasks({
        ids: this.deleteRows.map(item => item.id),
        revertSource: this.deleteRevertSource === '1'
      }).then(res => {
        this.$modal.msgSuccess((res.data && res.data.message) || '任务已删除')
        this.deleteDialogOpen = false
        this.getList()
      }).finally(() => { this.deleteSubmitting = false })
    },
    isUnknownResult(row) {
      return String(row.lastError || '').indexOf('GATEWAY_RESULT_UNKNOWN') === 0
    },
    taskTypeLabel(value) {
      const item = this.taskTypes.find(x => x.value === value)
      return item ? item.label : (value || '-')
    },
    taskTypeTag(value) {
      if (value === 'CUSTOMER_REPLY') return 'success'
      if (value === 'CUSTOMER_PUSH') return 'warning'
      if (value === 'UNMAPPED_CHAT') return 'danger'
      return 'info'
    },
    statusLabel(value) {
      const item = this.statuses.find(x => x.value === value)
      return item ? item.label : (value || '-')
    },
    statusTag(value) {
      if (value === 'SENT') return 'success'
      if (value === 'APPROVED_PENDING_SEND') return 'warning'
      if (value === 'NO_ACTION') return 'info'
      return ''
    },
    pretty(value) {
      try { return JSON.stringify(JSON.parse(value), null, 2) } catch (e) { return value || '-' }
    }
  }
}
</script>

<style scoped>
.detail-text { white-space: pre-wrap; word-break: break-all; line-height: 1.6; }
.json-box { white-space: pre-wrap; word-break: break-all; background: #f5f7fa; padding: 8px; margin: 0; max-height: 220px; overflow: auto; }
.preview-box { white-space: pre-wrap; word-break: break-all; background: #f5f7fa; padding: 6px; margin: 0; max-height: 240px; overflow: auto; }
.preview-party { color: #909399; font-size: 12px; }
.target-table { width: 100%; margin-top: 8px; }
.text-danger { color: #f56c6c; }
.text-muted { color: #909399; }
.mr5 { margin-right: 5px; }
.mt10 { margin-top: 10px; }
.selection-tip { margin-left: 10px; line-height: 28px; color: #909399; font-size: 12px; }
.task-switch { line-height: 28px; white-space: nowrap; }
.task-switch-label { margin-right: 6px; color: #606266; font-size: 13px; }
.dialog-section-title { margin: 16px 0 8px; font-weight: 600; color: #303133; }
.condition-list { margin: 0; padding-left: 22px; color: #606266; line-height: 1.8; }
.delete-question { margin: 18px 0 10px; font-weight: 600; }
.wechat-help-popover { line-height: 1.6; }
.wechat-help { max-height: 70vh; overflow-y: auto; padding-right: 6px; font-size: 12px; color: #303133; }
.wechat-help .help-title { margin: 10px 0 4px; color: #409eff; font-weight: 600; }
.wechat-help .help-title:first-child { margin-top: 0; }
.wechat-help ul { margin: 0; padding-left: 18px; }
.wechat-help li { margin: 3px 0; }
.wechat-help code { padding: 1px 4px; border-radius: 2px; background: #f5f7fa; color: #c7254e; }
</style>
