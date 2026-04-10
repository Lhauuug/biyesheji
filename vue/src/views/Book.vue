<template>
  <div class="home" style ="padding: 10px">

    <el-row :gutter="20" style="display: flex; align-items: stretch; min-height: calc(100vh - 130px);">
      <el-col :span="4">
        <el-card shadow="hover" style="height: 100%; border-radius: 8px;">
          <div style="font-size: 16px; font-weight: bold; margin-bottom: 15px; padding-bottom: 15px; border-bottom: 1px dashed #ebeef5; color: #409EFF; display: flex; align-items: center;">
            <span style="font-size: 20px; margin-right: 8px;">📚</span>
            图书全部分类
          </div>

          <el-tree
              :data="categoryData"
              :props="defaultProps"
              @node-click="handleNodeClick"
              highlight-current
              node-key="id"
              default-expand-all
              class="custom-tree"
          >
            <template #default="{ node, data }">
              <span
                  class="custom-tree-node"
                  :style="{
                  color: categoryId === data.id ? '#409EFF' : '#606266',
                  fontWeight: categoryId === data.id ? 'bold' : 'normal'
                }"
              >
                <span v-if="data.id === null" style="margin-right: 6px; font-size: 16px;">🌟</span>
                <span v-else-if="data.children && data.children.length > 0" style="margin-right: 6px; font-size: 16px;">📁</span>
                <span v-else style="margin-right: 6px; font-size: 16px;">🏷️</span>
                <span>{{ node.label }}</span>
              </span>
            </template>
          </el-tree>
        </el-card>
      </el-col>
      <el-col :span="20">

    <!-- 搜索-->
    <div style="margin: 10px 0;">
      <el-form inline="true" size="small">
        <el-form-item label="图书编号" >
          <el-input v-model="search1" placeholder="请输入图书编号"  clearable>
            <template #prefix><el-icon class="el-input__icon"><search/></el-icon></template>
          </el-input>
        </el-form-item >
        <el-form-item label="图书名称" >
          <el-input v-model="search2" placeholder="请输入图书名称"  clearable>
            <template #prefix><el-icon class="el-input__icon"><search /></el-icon></template>
          </el-input>
        </el-form-item >
        <el-form-item label="作者" >
          <el-input v-model="search3" placeholder="请输入作者"  clearable>
            <template #prefix><el-icon class="el-input__icon"><search /></el-icon></template>
          </el-input>
        </el-form-item >
        <el-form-item>
          <el-button type="primary" style="margin-left: 1%" @click="load" size="mini" >
            <svg-icon iconClass="search"/>查询</el-button>
        </el-form-item>
        <el-form-item>
          <el-button size="mini"  type="danger" @click="clear">重置</el-button>
        </el-form-item>
        <el-form-item style="float: right" v-if="numOfOutDataBook!=0">
          <el-popconfirm
              confirm-button-text="查看"
              cancel-button-text="取消"
              :icon="InfoFilled"
              icon-color="red"
              title="您有图书已逾期，请尽快归还"
              @confirm="toLook"
          >
            <template #reference>
              <el-button  type="warning">逾期通知</el-button>
            </template>
          </el-popconfirm>
        </el-form-item>
      </el-form>
    </div>
    <!-- 按钮-->
    <div style="margin: 10px 0;" >
      <el-button type="primary" @click = "add" v-if="user.role == 1">上架</el-button>
      <el-popconfirm title="确认下架?" @confirm="deleteBatch" v-if="user.role == 1">
        <template #reference>
          <el-button type="danger" size="mini" >批量下架</el-button>
        </template>
      </el-popconfirm>
    </div>
    <!-- 数据字段-->
    <el-table :data="tableData" stripe border="true" @selection-change="handleSelectionChange">
      <el-table-column v-if="user.role ==1"
                       type="selection"
                       width="55">
      </el-table-column>
      <el-table-column prop="isbn" label="图书编号" sortable />
      <el-table-column prop="name" label="图书名称" />
      <el-table-column prop="price" label="价格" sortable/>
      <el-table-column prop="author" label="作者" />
      <el-table-column prop="publisher" label="出版社" />
      <el-table-column prop="createTime" label="出版时间" sortable/>
      <el-table-column prop="borrownum" label="总借阅次数" sortable/>
      <el-table-column prop="status" label="状态">
          <template v-slot="scope">
            <el-tag v-if="scope.row.status == 0" type="warning">已借阅</el-tag>
            <el-tag v-else type="success">未借阅</el-tag>
          </template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="170">
          <template v-slot="scope">
          <el-button  size="mini" @click ="handleEdit(scope.row)" v-if="user.role == 1">修改</el-button>
          <el-popconfirm title="确认下架?" @confirm="handleDelete(scope.row.id)" v-if="user.role == 1">
            <template #reference>
              <el-button type="danger" size="mini" >下架</el-button>
            </template>
          </el-popconfirm>
          <el-button type="primary" size="mini" @click="handlelend(scope.row.id,scope.row.isbn,scope.row.name,scope.row.borrownum)" v-if="user.role == 2 && scope.row.status != 0">借阅</el-button>
          <el-button type="warning" size="mini" @click="handleReserve(scope.row)" v-if="user.role == 2 && scope.row.status == 0">预约</el-button>
          <el-popconfirm title="确认还书?" @confirm="handlereturn(scope.row.id,scope.row.isbn,scope.row.borrownum)" v-if="user.role == 2" :disabled="scope.row.status == 1">
            <template #reference>
              <el-button type="danger" size="mini" :disabled="(this.isbnArray.indexOf(scope.row.isbn)) == -1 ||scope.row.status == 1" >还书</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
<!--测试,通知对话框-->
    <el-dialog
        v-model="dialogVisible3"
        v-if="numOfOutDataBook!=0"
        title="逾期详情"
        width="50%"
        :before-close="handleClose"
    >
        <el-table :data="outDateBook" style="width: 100%">
          <el-table-column prop="isbn" label="图书编号" />
          <el-table-column prop="bookName" label="书名" />
          <el-table-column prop="lendtime" label="借阅日期" />
          <el-table-column prop="deadtime" label="截至日期" />
        </el-table>

      <template #footer>
      <span class="dialog-footer">
        <el-button type="primary" @click="dialogVisible3 = false"
        >确认</el-button>
      </span>
      </template>
    </el-dialog>
    <!--    分页-->
    <div style="margin: 10px 0">
      <el-pagination
          v-model:currentPage="currentPage"
          :page-sizes="[5, 10, 12]"
          :page-size="pageSize"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
      >
      </el-pagination>

      <el-dialog v-model="dialogVisible" title="上架书籍" width="30%">
        <el-form :model="form" label-width="120px">

          <el-form-item label="图书编号">
            <el-input style="width: 80%" v-model="form.isbn"></el-input>
          </el-form-item>
          <el-form-item label="图书名称">
            <el-input style="width: 80%" v-model="form.name"></el-input>
          </el-form-item>
          <el-form-item label="价格">
            <el-input style="width: 80%" v-model="form.price"></el-input>
          </el-form-item>
          <el-form-item label="作者">
            <el-input style="width: 80%" v-model="form.author"></el-input>
          </el-form-item>
          <el-form-item label="出版社">
            <el-input style="width: 80%" v-model="form.publisher"></el-input>
          </el-form-item>
          <el-form-item label="出版时间">
            <div>
              <el-date-picker value-format="YYYY-MM-DD" type="date" style="width: 80%" clearable v-model="form.createTime" ></el-date-picker>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
      <span class="dialog-footer">
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="save">确 定</el-button>
      </span>
        </template>
      </el-dialog>

      <el-dialog v-model="dialogVisible2" title="修改书籍信息" width="30%">
        <el-form :model="form" label-width="120px">

          <el-form-item label="图书编号">
            <el-input style="width: 80%" v-model="form.isbn"></el-input>
          </el-form-item>
          <el-form-item label="图书名称">
            <el-input style="width: 80%" v-model="form.name"></el-input>
          </el-form-item>
          <el-form-item label="价格">
            <el-input style="width: 80%" v-model="form.price"></el-input>
          </el-form-item>
          <el-form-item label="作者">
            <el-input style="width: 80%" v-model="form.author"></el-input>
          </el-form-item>
          <el-form-item label="出版社">
            <el-input style="width: 80%" v-model="form.publisher"></el-input>
          </el-form-item>
          <el-form-item label="出版时间">
            <div>
              <el-date-picker value-format="YYYY-MM-DD" type="date" style="width: 80%" clearable v-model="form.createTime" ></el-date-picker>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
      <span class="dialog-footer">
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="save">确 定</el-button>
      </span>
        </template>
      </el-dialog>
    </div>
      </el-col>
    </el-row>
  </div>
</template>

<script>
// @ is an alias to /src
import request from "../utils/request";
import {ElMessage} from "element-plus";
import moment from "moment";
import router from "@/router";
export default {
  created(){
    let userJson = sessionStorage.getItem("user")
    if(!userJson)
    {
      router.push("/login")
    }
    let userStr = sessionStorage.getItem("user") ||"{}"
    this.user = JSON.parse(userStr)
    let user = JSON.parse(sessionStorage.getItem("user"))
    this.phone= user.phone
    this.load()
    this.loadTree();
  },
  name: 'Book',
  methods: {

    // 加载分类树数据
    loadTree() {
      request.get("/category/tree").then(res => {
        // 自动在最顶上加一个“全部”按钮，方便用户看所有书
        this.categoryData = [{ id: null, name: '全部图书', children: [] }].concat(res.data || []);
      });
    },
    // 树节点点击事件
    handleNodeClick(data) {
      this.categoryId = data.id; // 记录点的是哪个分类
      this.load(); // 重新查右边的表格
    },
  // (this.isbnArray.indexOf(scope.row.isbn)) == -1
    handleSelectionChange(val){
      this.ids = val.map(v =>v.id)
    },
    deleteBatch(){
      if (!this.ids.length) {
        ElMessage.warning("请选择数据！")
        return
      }
      //  一个小优化，直接发送这个数组，而不是一个一个的提交下架
      request.post("/book/deleteBatch",this.ids).then(res =>{
        if(res.code === '0'){
          ElMessage.success("批量下架成功")
          this.load()
        }
        else {
          ElMessage.error(res.msg)
        }
      })
    },
    load(){
      this.numOfOutDataBook =0;
      this.outDateBook =[];
      request.get("/book",{
        params:{
          pageNum: this.currentPage,
          pageSize: this.pageSize,
          search1: this.search1,
          search2: this.search2,
          search3: this.search3,
          categoryId: this.categoryId,
        }
      }).then(res =>{
        console.log(res)
        this.tableData = res.data.records
        this.total = res.data.total
      })
    //
      if(this.user.role == 2){
        request.get("/bookwithuser",{
          params:{
            pageNum: "1",
            pageSize: this.total,
            search1: "",
            search2: "",
            search3: this.user.id,
          }
        }).then(res =>{
          console.log(res)
          this.bookData = res.data.records
          this.number = this.bookData.length;
          var nowDate = new Date();
          for(let i=0; i< this.number; i++){
            this.isbnArray[i] = this.bookData[i].isbn;
            let dDate = new Date(this.bookData[i].deadtime);
            if(dDate < nowDate){
              this.outDateBook[this.numOfOutDataBook] = {
                isbn:this.bookData[i].isbn,
                bookName : this.bookData[i].bookName,
                deadtime : this.bookData[i].deadtime,
                lendtime : this.bookData[i].lendtime,
              };
              this.numOfOutDataBook = this.numOfOutDataBook + 1;
            }
          }
          console.log("in load():" +this.numOfOutDataBook );
        })
      }
      request.get("/user/alow/"+this.user.id).then(res=>{
        if (res.code == 0) {
          this.flag = true
        }
        else {
          this.flag = false
        }
      })
      //判断是否具有借阅权力
    },
    clear(){
      this.search1 = ""
      this.search2 = ""
      this.search3 = ""
      this.load()
      this.loadTree();
    },

    handleDelete(id){
      request.delete("book/" + id ).then(res =>{
        console.log(res)
        if(res.code == 0 ){
          ElMessage.success("下架成功")
        }
        else
          ElMessage.error(res.msg)
        this.load()
      })
    },
    handlereturn(id,isbn,bn){
      this.form.status = "1"
      this.form.id = id
      request.put("/book",this.form).then(res =>{
        console.log(res)
        if(res.code == 0){
          ElMessage({
            message: '还书成功',
            type: 'success',
          })
        }
        else {
          ElMessage.error(res.msg)
        }
      //
        this.form3.isbn = isbn
        this.form3.readerId = this.user.id
        let endDate = moment(new Date()).format("yyyy-MM-DD HH:mm:ss")
        this.form3.returnTime = endDate
        this.form3.status = "1"
        console.log(bn)
        this.form3.borrownum = bn
        request.put("/LendRecord1/",this.form3).then(res =>{
          console.log(res)
          let form3 ={};
          form3.isbn = isbn;
          form3.bookName = name;
          form3.nickName = this.user.username;
          form3.id = this.user.id;
          form3.lendtime = endDate;
          form3.deadtime = endDate;
          form3.prolong  = 1;
          request.post("/bookwithuser/deleteRecord",form3).then(res =>{
            console.log(res)
            this.load()
          })

        })
      //
      })
    },

    // 👇👇 这是新加的预约排队方法 👇👇
    handleReserve(row) {
      if (!this.user || !this.user.id) {
        ElMessage.error("请先登录！");
        return;
      }

      let reserveData = {
        userId: this.user.id,
        bookId: row.id
      };

      // 调后端的预约接口
      request.post('/reserve/add', reserveData).then(res => {
        if (res.code === 0 || res.code === '0') {
          ElMessage.success(res.msg || "预约排队成功！图书归还后将邮件通知您。");
          this.load(); // 刷新列表
        } else {
          ElMessage.error(res.msg || "预约失败");
        }
      });
    },
    // 👆👆 预约排队方法结束 👆👆

    handlelend(id,isbn,name,bn){

      if (this.phone == null){
        ElMessage.error("借阅失败! 请先将个人信息补充完整")
        this.$router.push("/person")//跳转个人信息界面
        return;
      }

      if(this.number ==5){
        ElMessage.warning("您不能再借阅更多的书籍了")
        return;
      }
      if(this.numOfOutDataBook !=0){
        ElMessage.warning("在您归还逾期书籍前不能再借阅书籍")
        return;
      }

      if(this.flag == false){
        ElMessage({
          message: '您没有借阅权限,管理员审核通过后授权',
          type: 'error',
        })
        return;
      }

      this.form.status = "0"
      this.form.id = id
      this.form.borrownum = bn+1
      console.log(bn)
      request.put("/book",this.form).then(res =>{
        console.log(res)
        if(res.code == 0){
          ElMessage({
            message: '借阅成功',
            type: 'success',
          })
        }
        else {
          ElMessage.error(res.msg)
        }
      })

      this.form2.status = "0"
      this.form2.isbn = isbn
      this.form2.bookname = name
      this.form2.readerId = this.user.id
      this.form2.borrownum = bn+1
      console.log(this.form2.borrownum)
      console.log(this.user)
      let startDate = moment(new Date()).format("yyyy-MM-DD HH:mm:ss");
      this.form2.lendTime = startDate
      console.log(this.user)
      request.post("/LendRecord",this.form2).then(res =>{
        console.log(res)
        this.load();

      })
      let form3 ={};
      form3.isbn = isbn;
      form3.bookName = name;
      form3.nickName = this.user.username;
      form3.id = this.user.id;
      form3.lendtime = startDate;
      let nowDate = new Date(startDate);
      nowDate.setDate(nowDate.getDate()+30);
      form3.deadtime = moment(nowDate).format("yyyy-MM-DD HH:mm:ss");
      form3.prolong  = 1;
      request.post("/bookwithuser/insertNew",form3).then(res =>{
        console.log(res)
        this.load()
      })
    },
    add(){
      this.dialogVisible= true
      this.form ={}
    },
    save(){
      //ES6语法
      //地址,但是？IP与端口？+请求参数
      // this.form?这是自动保存在form中的，虽然显示时没有使用，但是这个对象中是有它的
      if(this.form.id){
        request.put("/book",this.form).then(res =>{
          console.log(res)
          if(res.code == 0){
            ElMessage({
              message: '修改书籍信息成功',
              type: 'success',
            })
          }
          else {
            ElMessage.error(res.msg)
          }

          this.load()
          this.dialogVisible2 = false
        })
      }
      else {
        this.form.borrownum = 0
        this.form.status = 1
        request.post("/book",this.form).then(res =>{
          console.log(res)
          if(res.code == 0){
            ElMessage.success('上架书籍成功')
          }
          else {
            ElMessage.error(res.msg)
          }
          this.load()
          this.dialogVisible = false
        })
      }

    },
    // formatter(row) {:formatter="formatter"
    //   return row.address
    // },

    handleEdit(row){
      this.form = JSON.parse(JSON.stringify(row))
      this.dialogVisible2 = true
    },
    handleSizeChange(pageSize){
      this.pageSize = pageSize
      this.load()
    },
    handleCurrentChange(pageNum){
      this.pageNum = pageNum
      this.load()
    },
    toLook(){
      this.dialogVisible3 =true;
    },
  },
  data() {
    return {
      categoryData: [],
      defaultProps: {
        children: 'children',
        label: 'name'
      },
      categoryId: null,
      phone:'',
      flag:'',
      form: {},
      form2:{},
      form3:{},
      dialogVisible: false,
      dialogVisible2: false,
      search1:'',
      search2:'',
      search3:'',
      total:10,
      currentPage:1,
      pageSize: 10,
      tableData: [],
      user:{},
      number:0,
      bookData:[],
      isbnArray:[],
      outDateBook:[],
      numOfOutDataBook: 0,
      dialogVisible3 : true,
    }
  },
}
</script>
<style scoped>
/* 保证树控件占满宽度 */
.custom-tree {
  width: 100%;
}

/* 节点容器布局 */
.custom-tree-node {
  display: flex;
  align-items: center;
  font-size: 14px;
  width: 100%;
}

/* 🚀 核心修复：强行固定图标的宽度！解决图标大小不一导致的文字对不齐 */
.custom-tree-node > span:first-child {
  display: inline-block;
  width: 24px;         /* 强制所有图标占位一模一样宽 */
  text-align: center;  /* 图标在自己的格子里居中 */
  margin-right: 4px;   /* 和文字的距离 */
}

/* 调整完美的行高和圆角 */
:deep(.el-tree-node__content) {
  height: 40px !important;
  border-radius: 6px;
  margin: 2px 0; /* 只保留上下间距，左右贴边，保证背景条横平竖直 */
}

/* 悬浮时的背景色 */
:deep(.el-tree-node__content:hover) {
  background-color: #f5f7fa !important;
}

/* 选中时的背景色 */
:deep(.el-tree-node.is-current > .el-tree-node__content) {
  background-color: #ecf5ff !important;
}

/* 选中时的文字变蓝 + 加粗 */
:deep(.el-tree-node.is-current > .el-tree-node__content .custom-tree-node) {
  color: #409EFF !important;
  font-weight: bold !important;
}

/* 让原生的小三角箭头变灰，不抢视觉重心 */
:deep(.el-tree-node__expand-icon) {
  color: #909399;
}
</style>