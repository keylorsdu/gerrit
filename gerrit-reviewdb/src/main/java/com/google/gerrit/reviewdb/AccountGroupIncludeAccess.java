// Copyright (C) 2011 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Access;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.PrimaryKey;
import com.google.gwtorm.client.Query;
import com.google.gwtorm.client.ResultSet;

public interface AccountGroupIncludeAccess extends
    Access<AccountGroupInclude, AccountGroupInclude.Key> {
  @PrimaryKey("key")
  AccountGroupInclude get(AccountGroupInclude.Key key) throws OrmException;

  @Query("WHERE key.includeId = ?")
  ResultSet<AccountGroupInclude> byInclude(AccountGroup.Id id) throws OrmException;

  @Query("WHERE key.groupId = ?")
  ResultSet<AccountGroupInclude> byGroup(AccountGroup.Id id) throws OrmException;
}
