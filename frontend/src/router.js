
import Vue from 'vue'
import Router from 'vue-router'

Vue.use(Router);


import ReservationManager from "./components/ReservationManager"

import ApprovalManager from "./components/ApprovalManager"


import Mycourt from "./components/Mycourt"
export default new Router({
    // mode: 'history',
    base: process.env.BASE_URL,
    routes: [
            {
                path: '/reservations',
                name: 'ReservationManager',
                component: ReservationManager
            },

            {
                path: '/approvals',
                name: 'ApprovalManager',
                component: ApprovalManager
            },


            {
                path: '/mycourts',
                name: 'Mycourt',
                component: Mycourt
            },


    ]
})
