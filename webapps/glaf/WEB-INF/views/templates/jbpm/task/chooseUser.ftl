<!DOCTYPE html>
<html>
<title>流程任务</title> 
<link rel="stylesheet" type="text/css" href="${request.contextPath}/static/css/core.css"/>
<script type="text/javascript" src="${request.contextPath}/static/scripts/jquery.min.js"></script>
<script type="text/javascript" src="${request.contextPath}/static/scripts/jquery.form.js"></script>
<script language="javascript">

 function addElement() {
		if(document.getElementById("taskName").value==""){
			   alert("提示:请选择要重新分派任务的名称！");
			   document.getElementById("taskName").focus();
			   return;
		 }
        var list = document.iForm.noselected;
        for (i = 0; i < list.length; i++) {
            if (list.options[i].selected) {
                var value = list.options[i].value;
                var text = list.options[i].text;
                addToList(value, text);
				list.remove(i);
				i=i-1;
            }
        }

    }

 function addToList(value, text) {
        var list = document.iForm.selected;
        if (list.length > 0) {
            for (k = 0; k < list.length; k++) {
                if (list.options[k].value == value) {
                    return;
                }
            }
        }

        var len = list.options.length;
        list.length = len + 1;
        list.options[len].value = value;
        list.options[len].text = text;
    }

 function removeElement() {
		if(document.getElementById("taskName").value==""){
			   alert("提示:请选择要重新分派任务的名称！");
			   document.getElementById("taskName").focus();
			   return;
		 }
        var list = document.iForm.selected;
		var slist = document.iForm.noselected;
        if (list.length == 0 || list.selectedIndex < 0 || list.selectedIndex >= list.options.length)
            return;

        for (i = 0; i < list.length; i++) {
            if (list.options[i].selected) {
			    var value = list.options[i].value;
                var text = list.options[i].text;
                list.options[i] = null;
                i--;
				var len = slist.options.length;
				slist.length = len+1;
                slist.options[len].value = value;
                slist.options[len].text = text;				
            }
        }
    }


  function submitRequest() {
 
   if(document.getElementById("taskName").value==""){
	   alert("提示:请选择要重新分派任务的名称！");
	   document.getElementById("taskName").focus();
	   return;
   }

    var len=document.iForm.selected.length;
	var result="";
	for (var i=0;i<len;i++) {
      result = result+","+document.iForm.selected.options[i].value;
    }

	document.getElementById("actorIdXY").value = result;

	if(confirm("系统将重新分配任务，确认吗?")){
          var params = jQuery("#iForm").formSerialize();
			  jQuery.ajax({
				   type: "POST",
				   url: '${request.contextPath}/jbpm/task/reassign',
				   data: params,
				   dataType: 'json',
				   error: function(data){
					   alert('服务器处理错误！');
				   },
				   success: function(data){
						 alert('操作成功完成！');
				   }
			 });
	   }

  }

  function chooseUser(){
        document.iForm.action="${request.contextPath}/jbpm/task/chooseUser";
		document.iForm.submit();
  }

  function goback(){
	    location.href='${request.contextPath}/jbpm/task/task?processInstanceId=${processInstanceId}';
  }
</script>
<body leftmargin="0" topmargin="0" marginheight="0" marginwidth="0">
<center>
<form id="iForm" name="iForm" method="post" class="x-form">
<input type="hidden" id="actorIdXY" name="actorIdXY">
<input type="hidden" id="processInstanceId" name="processInstanceId" value="${processInstanceId}">
<div class="content-block" style="width: 685px;"> 
<div class="x_content_title">
<img src="${request.contextPath}/static/images/window.png" alt="任务分派">任务分派
</div>
<fieldset class="x-fieldset" style="width: 95%;">
<table class="table-border" align="center" cellpadding="4"
	cellspacing="1" width="90%">
	<tbody>
		<tr>
			<td align="center" colspan="5" valign="top">&nbsp;</td>
		</tr>
		<tr>
			<td colspan="5">
			  <div align="center">&nbsp;请选择要重新分派任务的名称 
			   <select class="select"
				id="taskName" name="taskName" onchange="javascript:chooseUser();">
				<option value="">----请选择----</option>
				${taskNameScript}
			  </select>
			  </div>
			</td>
		</tr>
		<tr>
			<td class="table-content" height="26" valign="top" width="220">
			<div align="center">
			<div align="center"><br>
			可选人员<br>
			</div>
			<select class="select" style="width: 250px; height: 320px;"
				multiple="multiple" size="12" name="noselected"
				ondblclick="javascript:addElement();">
				${noselectedScript}
			</select></div>
			</td>
			<td width="90">
			<div align="center">
			<input name="add" value="添加->"
				onClick="javascript:addElement();" class="btn btnGray" type="button">
			<br>
			<br>
			<input name="remove" value="<-删除" 
			       onClick="javascript:removeElement();" class="btn btnGray" type="button">
			</div>
			</td>
			<td class="table-content" height="26" valign="top" width="220">
			<div align="center">
			<div align="center"><br>
			已选人员<br>
			</div>
			<select class="select" style="width: 250px; height: 320px;"
				multiple="multiple" size="12" name="selected"
				ondblclick="javascript:removeElement();">
				${selectedScript}
			</select></div>
			</td>
		</tr>
	</tbody>
</table>

</fieldset>


<div align="center"><br />
<input value="确 定" class="btn btnGray" name="button" type="button"
	onclick="javacsript:submitRequest();"> <input value="返 回"
	class="btn btnGray" name="button" type="button"
	onclick="javacsript:goback();"> <br />
<br />
</div>

</div>
</form>
</center>