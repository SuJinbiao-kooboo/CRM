export const ORDER_FILE_GROUPS = [
  {
    key: 'customer',
    title: '客户文件',
    subtitle: '客户侧：PO / PI / CI / 装箱单 / 委托单 / 签收单 / 照片 / SN',
    slots: [
      { prop: 'poCustomerFile', label: 'PO', hint: '客户PO' },
      { prop: 'piOurFile', label: 'PI', hint: '我方PI' },
      { prop: 'ciOurFile', label: 'CI', hint: '我方CI' },
      { prop: 'plOurFile', label: '装箱单', hint: '我方装箱单' },
      { prop: 'entrustFile', label: '委托单', hint: '客户委托单' },
      { prop: 'signReceiptFile', label: '签收单', hint: '客户签收单' },
      { prop: 'photoFile', label: '照片存档', hint: '客户照片' },
      { prop: 'snFile', label: 'SN文件', hint: '客户SN' }
    ]
  },
  {
    key: 'supplier',
    title: '供应商文件',
    subtitle: '供应商侧：PO / PI / CI / 装箱单 / 委托单 / 签收单 / 照片 / SN',
    slots: [
      { prop: 'poOurFile', label: 'PO', hint: '我方PO' },
      { prop: 'piSupplierFile', label: 'PI', hint: '供应商PI' },
      { prop: 'ciSupplierFile', label: 'CI', hint: '供应商CI' },
      { prop: 'plSupplierFile', label: '装箱单', hint: '供应商装箱单' },
      { prop: 'entrustSupplierFile', label: '委托单', hint: '供应商委托单' },
      { prop: 'signReceiptSupplierFile', label: '签收单', hint: '供应商签收单' },
      { prop: 'photoSupplierFile', label: '照片存档', hint: '供应商照片' },
      { prop: 'snSupplierFile', label: 'SN文件', hint: '供应商SN' }
    ]
  }
]

export const ORDER_FILE_SLOTS = ORDER_FILE_GROUPS.reduce((slots, group) => slots.concat(group.slots), [])
