/**
 * @ngdoc function
 * @name fstepApp.controller:CommunityFilesCtrl
 * @description
 * # CommunityFilesCtrl
 * Controller of the fstepApp
 */

'use strict';

define(['../../../fstepmodules'], function (fstepmodules) {

    fstepmodules.controller('CommunityFilesCtrl', ['FileService', 'CommonService', '$scope', '$mdDialog', function (FileService, CommonService, $scope, $mdDialog) {

        /* Get stored Files details */
        $scope.fileParams = FileService.params.community;
        $scope.fileOwnershipFilters = FileService.fileOwnershipFilters;
        $scope.item = "File";
        $scope.filetypes = [
            { name: "Reference Data", value: "REFERENCE_DATA" },
            { name: "Output Products", value: "OUTPUT_PRODUCT" }
        ];

        /* Get files */
        FileService.refreshFstepFiles("community");

        /* Update files when polling */
        $scope.$on('poll.fstepfiles', function (event, data) {
            $scope.fileParams.files = data;
        });

        /* Stop Polling */
        $scope.$on("$destroy", function() {
            FileService.stopPolling();
        });

        /* Paging */
        $scope.getPage = function(url){
            FileService.getFstepFilesPage('community', url);
        };

        $scope.filter = function(){
            FileService.getFstepFilesByFilter('community');
        };

        /* Select a File */
        $scope.selectFile = function (item) {
            $scope.fileParams.selectedFile = item;
            FileService.refreshSelectedFstepFile("community");
        };

        /* Filters */
        $scope.toggleFilters = function () {
            $scope.fileParams.displayFilters = !$scope.fileParams.displayFilters;
        };

        /* Add reference data */
        $scope.addReferenceFileDialog = function ($event) {
            function AddReferenceFileDialog($scope, $mdDialog, FileService) {

                $scope.item = "File";
                $scope.fileParams = FileService.params.community;
                $scope.newReference = {};
                $scope.validation = "Valid";

                $scope.validateFile = function (file) {
                    if(!file) {
                        $scope.validation = "No file selected";
                    } else if (file.name.indexOf(' ') >= 0) {
                        $scope.validation = "Filename cannot contain white space";
                    } else if (file.size >= (1024*1024*1024*2)) {
                        $scope.validation = "Filesize cannot exceed 2GB";
                    } else {
                        $scope.validation = "Valid";
                    }
                };

                /* Upload the file */
                $scope.addReferenceFile = function () {
                    FileService.uploadFile("community", $scope.newReference).then(function (response) {
                        /* Get updated list of reference data */
                        FileService.refreshFstepFiles("community");
                    });
                };

                $scope.closeDialog = function () {
                    $mdDialog.hide();
                };

            }
            AddReferenceFileDialog.$inject = ['$scope', '$mdDialog', 'FileService'];
            $mdDialog.show({
                controller: AddReferenceFileDialog,
                templateUrl: 'views/community/templates/addreferencedata.tmpl.html',
                parent: angular.element(document.body),
                targetEvent: $event,
                clickOutsideToClose: true
            });
        };

        /* Remove File */
        $scope.removeItem = function (event, key, item) {
            CommonService.confirm(event, 'Are you sure you want to delete this file?').then(function (confirmed) {
                if (confirmed !== false) {
                    FileService.removeFstepFile(item).then(function (data) {
                        /* Update list of files */
                        FileService.refreshFstepFiles("community", "Remove", item);
                    });
                }
            });
        };

    }]);
});
