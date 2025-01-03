function doGet(e) {
  Logger.log(JSON.stringify(e));
  var result = 'OK';
  if (e.parameter == 'undefined') {
    result = 'No_Parameters';
  } else {
    var sheet_id = '1LGurFqwVQFQWLhPeWektSzSZD9L-V2xRF7FnpQ8N2F0'; // Spreadsheet ID
    var sheet_UD = 'User_Data'; // Sheet name for user data
    var sheet_AT = 'Attendance'; // Sheet name for attendance
    var sheet_BM = 'Biometrics'; // Sheet name for biometrics
    var sheet_open = SpreadsheetApp.openById(sheet_id);
    var sheet_user_data = sheet_open.getSheetByName(sheet_UD);
    var sheet_attendance = sheet_open.getSheetByName(sheet_AT);
    var sheet_biometrics = sheet_open.getSheetByName(sheet_BM);

    var sts_val = "";
    var uid_val = "";
    var fbm_val = "";
    var uid_column = "B";
    var fbm_column = "C";
    var TI_val = "";
    var Date_val = "";

    for (var param in e.parameter) {
      Logger.log('In for loop, param=' + param);
      var value = stripQuotes(e.parameter[param]);
      Logger.log(param + ':' + e.parameter[param]);
      switch (param) {
        case 'sts':
          sts_val = value;
          break;
        case 'uid':
          uid_val = value;
          break;
        case 'fbm':
          fbm_val = value;
          break;
        default:
      }
    }

    // Registration logic remains unchanged
    if (sts_val == 'reg') {
      var check_new_UID = checkUID(sheet_id, sheet_UD, 2, uid_val);
      var check_new_FBM = checkFBM(sheet_id, sheet_UD, 3, fbm_val);

      if (check_new_UID == true && check_new_FBM == true) {
        result += ",regErr01"; // Err_01 = UID and FBM are already registered
        return ContentService.createTextOutput(result);
      } else if (check_new_UID == true) {
        result += ",regErr02"; // Err_02 = UID is already registered
        return ContentService.createTextOutput(result);
      } else if (check_new_FBM == true) {
        result += ",regErr03"; // Err_03 = FBM is already registered
        return ContentService.createTextOutput(result);
      }

      var getLastRowUIDCol = findLastRow(sheet_id, sheet_UD, uid_column);
      var getLastRowFBMCol = findLastRow(sheet_id, sheet_UD, fbm_column);
      var newUID = sheet_user_data.getRange(uid_column + (getLastRowUIDCol + 1));
      var newFBM = sheet_user_data.getRange(fbm_column + (getLastRowFBMCol + 1));
      newUID.setValue(uid_val);
      newFBM.setValue(fbm_val);
      result += ",R_Successful_UID," + uid_val + ",R_Successful_FBM," + fbm_val;
      return ContentService.createTextOutput(result);
    }

    // Attendance tracking logic - now separate from biometrics
    if (sts_val == 'atc') {
      var FUID = findUID(sheet_id, sheet_UD, 2, uid_val);
      if (FUID == -1) {
        result += ",atcErr01"; // atcErr01 = UID not registered
        return ContentService.createTextOutput(result);
      } else {
        // Get user data for attendance only
        var userData = getUserData(sheet_user_data, FUID + 2);
        
        var enter_data = "time_in";
        var num_row;
        var Curr_Date = Utilities.formatDate(new Date(), "Asia/Manila", 'dd/MM/yyyy');
        var Curr_Time = Utilities.formatDate(new Date(), "Asia/Manila", 'HH:mm:ss');
        var data = sheet_attendance.getDataRange().getDisplayValues();

        if (data.length > 1) {
          for (var i = 0; i < data.length; i++) {
            if (data[i][1] == uid_val) {
              if (data[i][2] == Curr_Date) {
                if (data[i][4] == "") {
                  Date_val = data[i][2];
                  TI_val = data[i][3];
                  enter_data = "time_out";
                  num_row = i + 1;
                  break;
                } else {
                  enter_data = "finish";
                }
              }
            }
          }
        }

        if (enter_data == "time_in") {
          var status = calculateStatus(Curr_Time);
          sheet_attendance.insertRows(2);
          sheet_attendance.getRange("A2").setValue(userData.name);          
          sheet_attendance.getRange("B2").setValue(uid_val);
          sheet_attendance.getRange("C2").setValue(Curr_Date);             
          sheet_attendance.getRange("D2").setValue(Curr_Time);             
          sheet_attendance.getRange("F2").setValue(status);                
          SpreadsheetApp.flush();
          result += ",TI_Successful" + "," + userData.name + "," + Curr_Date + "," + Curr_Time + "," + status;
          return ContentService.createTextOutput(result);
        }

        if (enter_data == "time_out") {
          sheet_attendance.getRange("E" + num_row).setValue(Curr_Time);
          result += ",TO_Successful" + "," + userData.name + "," + Date_val + "," + TI_val + "," + Curr_Time;
          return ContentService.createTextOutput(result);
        }

        if (enter_data == "finish") {
          result += ",atcInf01"; // atcInf01 = You have completed your attendance record for today
          return ContentService.createTextOutput(result);
        }
      }
    }

    if (sts_val == 'bm') {
  var fbm_val = e.parameter.fbm ? stripQuotes(e.parameter.fbm) : ""; //Handle missing fbm parameter

  if (fbm_val === "") {
    result += ",bmErr04"; //New error code for missing FBM parameter
    return ContentService.createTextOutput(result);
  }

  var userIndex = findFBM(sheet_id, sheet_UD, 3, fbm_val); //Use column 3 for FBM

  if (userIndex == -1) {
    result += ",bmErr01"; // FBM not registered
    return ContentService.createTextOutput(result);
  } else {
    var userData = getUserData(sheet_user_data, userIndex + 2); //Get data using index

    // Insert a new row in the Biometrics sheet for the specific user
    var nextRow = sheet_biometrics.getLastRow() + 1;
    sheet_biometrics.insertRowAfter(nextRow -1); //Insert after last row
    sheet_biometrics.getRange("A" + nextRow).setValue(userData.name);
    sheet_biometrics.getRange("B" + nextRow).setValue(userData.fbm);
    sheet_biometrics.getRange("C" + nextRow).setValue(userData.studentNumber);
    sheet_biometrics.getRange("D" + nextRow).setValue(userData.yearLevel);
    sheet_biometrics.getRange("E" + nextRow).setValue(userData.course);
    SpreadsheetApp.flush();
    result += ",BM_Successful," + userData.name; //Indicate successful single user input
    return ContentService.createTextOutput(result);
  }
}

  }
}

// Helper functions remain unchanged
function getUserData(sheet, column) {
  var userData = {
    name: sheet.getRange("A" + column).getValue(),
    studentNumber: sheet.getRange("D" + column).getValue(),
    fbm: sheet.getRange("C" + column).getValue(),
    yearLevel: sheet.getRange("E" + column).getValue(),
    course: sheet.getRange("F" + column).getValue()
  };
  return userData;
}

function stripQuotes(value) {
  return value.replace(/^[\"']|[\"']$/g, "");
}

function findLastRow(id_sheet, name_sheet, name_column) {
  var spreadsheet = SpreadsheetApp.openById(id_sheet);
  var sheet = spreadsheet.getSheetByName(name_sheet);
  var lastRow = sheet.getLastRow();
  var range = sheet.getRange(name_column + lastRow);
  if (range.getValue() !== "") {
    return lastRow;
  } else {
    return range.getNextDataCell(SpreadsheetApp.Direction.UP).getRow();
  }
}

function findUID(id_sheet, name_sheet, column_index, searchString) {
  var open_sheet = SpreadsheetApp.openById(id_sheet);
  var sheet = open_sheet.getSheetByName(name_sheet);
  var columnValues = sheet.getRange(2, column_index, sheet.getLastRow()).getValues();
  var searchResult = columnValues.findIndex(searchString);  // Row Index - 2
  return searchResult;
}

function findFBM(id_sheet, name_sheet, column_index, searchString) {
  var open_sheet = SpreadsheetApp.openById(id_sheet);
  var sheet = open_sheet.getSheetByName(name_sheet);
  var columnValues = sheet.getRange(2, column_index, sheet.getLastRow()).getValues();
  var searchResult = columnValues.findIndex(searchString);  // Row Index - 2
  return searchResult;
}


function checkUID(id_sheet, name_sheet, column_index, searchString) {
  var open_sheet = SpreadsheetApp.openById(id_sheet);
  var sheet = open_sheet.getSheetByName(name_sheet);
  var columnValues = sheet.getRange(2, column_index, sheet.getLastRow()).getValues();
  var searchResult = columnValues.findIndex(function(value) {
    return value[0] == searchString;
  });
  if (searchResult != -1) {
    return true;
  } else {
    return false;
  }
}

function checkFBM(id_sheet, name_sheet, column_index, searchString) {
  var open_sheet = SpreadsheetApp.openById(id_sheet);
  var sheet = open_sheet.getSheetByName(name_sheet);
  var columnValues = sheet.getRange(2, column_index, sheet.getLastRow()).getValues();
  var searchResult = columnValues.findIndex(function(value) {
    return value[0] == searchString;
  });
  if (searchResult != -1) {
    return true;
  } else {
    return false;
  }
}

Array.prototype.findIndex = function(search) {
  if (search == "") return false;
  for (var i = 0; i < this.length; i++)
    if (this[i].toString().indexOf(search) > -1) return i;
  return -1;
}

function calculateStatus(currentTime) {
  var onTimeThreshold = "07:45:00";
  var lateThreshold = "07:55:00";

  if (currentTime <= onTimeThreshold) {
    return "On-time";
  } else if (currentTime <= lateThreshold) {
    return "Late";
  } else {
    return "Absent";
  }
} 