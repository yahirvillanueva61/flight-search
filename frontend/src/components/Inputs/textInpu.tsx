import React from 'react';
import { Flex, Input } from 'antd';

const App: React.FC = () => (
  <Flex vertical gap={12}>
    <Input.Search placeholder="Filled" variant="filled" />
  </Flex>
);

export default App;