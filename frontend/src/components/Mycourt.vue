<template>

<v-data-table
    :headers="headers"
    :items="mycourt"
    :items-per-page="5"
    class="elevation-1"
  ></v-data-table>

</template>

<script>
  const axios = require('axios').default;

  export default {
    name: 'Mycourt',
    props: {
      value: Object,
      editMode: Boolean,
      isNew: Boolean
    },
    data: () => ({
        headers: [
            { text: "id", value: "id" },
            { text: "reservationId", value: "reservationId" },
            { text: "approvalId", value: "approvalId" },
            { text: "courtName", value: "courtName" },
            { text: "playerName", value: "playerName" },
            { text: "time", value: "time" },
            { text: "status", value: "status" },
        ],
        mycourt : [],
    }),
    async created() {
      var temp = await axios.get(axios.backend + '/mycourts')

      this.mycourt = temp.data._embedded.mycourts;

    },
    methods: {
    }
  }
</script>

