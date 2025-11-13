/**
 * 字典数据处理
 */
import { getDicts } from '@/api/system/dict/data'

const state = {
  dict: new Array()
}

const mutations = {
  SET_DICT: (state, { key, value }) => {
    if (key !== null && key !== "") {
      state.dict.push({
        key: key,
        value: value
      })
    }
  },
  REMOVE_DICT: (state, key) => {
    try {
      for (let i = 0; i < state.dict.length; i++) {
        if (state.dict[i].key == key) {
          state.dict.splice(i, 1)
          return true
        }
      }
    } catch (e) {
      console.log(e)
    }
  },
  CLEAN_DICT: (state) => {
    state.dict = new Array()
  }
}

const actions = {
  // 设置字典
  setDict({ commit }, data) {
    commit('SET_DICT', data)
  },
  // 删除字典
  removeDict({ commit }, key) {
    commit('REMOVE_DICT', key)
  },
  // 清空字典
  cleanDict({ commit }) {
    commit('CLEAN_DICT')
  },
  // 获取字典
  getDict({ commit, state }, key) {
    if (key == null && key == "") {
      return null
    }
    try {
      for (let i = 0; i < state.dict.length; i++) {
        if (state.dict[i].key == key) {
          return state.dict[i].value
        }
      }
    } catch (e) {
      return null
    }
    return new Promise((resolve, reject) => {
      getDicts(key).then(res => {
        res.data = res.data.map(p => ({ label: p.dictLabel, value: p.dictValue, elTagType: p.listClass, elTagClass: p.cssClass }))
        commit('SET_DICT', {
          key: key,
          value: res.data
        })
        resolve(res.data)
      }).catch(error => {
        reject(error)
      })
    })
  }
}

export default {
  namespaced: true,
  state,
  mutations,
  actions
}